package reactor.core.publisher;

import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.LLLLL_Result;
import org.openjdk.jcstress.infra.results.LLLL_Result;
import reactor.core.CoreSubscriber;

import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE;

public abstract class FluxSwitchOnFirstConditionalStressTest {

	final FluxSwitchOnFirstStressTest.StressSubscription<String> inboundSubscription =
			new FluxSwitchOnFirstStressTest.StressSubscription<>();
	final ConditionalStressSubscriber<String>                    inboundSubscriber   =
			new ConditionalStressSubscriber<>(0);

	final FluxSwitchOnFirstStressTest.StressSubscription<String> outboundSubscription =
			new FluxSwitchOnFirstStressTest.StressSubscription<>();
	final ConditionalStressSubscriber<String>                    outboundSubscriber   =
			new ConditionalStressSubscriber<>(0);

	final FluxSwitchOnFirst.SwitchOnFirstConditionalMain<String, String> main =
			new FluxSwitchOnFirst.SwitchOnFirstConditionalMain<String, String>(
					outboundSubscriber,
					this::switchOnFirst,
					false);

	{
		inboundSubscription.subscribe(main);
	}

	abstract Flux<String> switchOnFirst(Signal<? extends String> signal,
			Flux<String> inbound);

	@JCStressTest
	@Outcome(id = {"1, 1, 1, 1, 1"}, expect = ACCEPTABLE)
	@State
	public static class OutboundOnSubscribeAndRequestStressTest
			extends FluxSwitchOnFirstConditionalStressTest {

		@Override
		Flux<String> switchOnFirst(Signal<? extends String> signal,
				Flux<String> inbound) {
			return new Flux<String>() {
				@Override
				public void subscribe(CoreSubscriber<? super String> actual) {
					inbound.subscribe(inboundSubscriber);
					inboundSubscriber.request(1);
					outboundSubscription.subscribe(actual);
				}
			};
		}

		@Actor
		public void next() {
			main.tryOnNext("test");
		}

		@Actor
		public void request() {
			outboundSubscriber.request(1);
		}

		@Arbiter
		public void arbiter(LLLLL_Result result) {
			result.r1 = outboundSubscription.requestsCount;
			result.r2 = outboundSubscription.requested;
			result.r3 = inboundSubscription.requestsCount;
			result.r4 = inboundSubscription.requested;
			result.r5 = inboundSubscriber.onNextCalls;
		}
	}

	@JCStressTest
	@Outcome(id = {
			"1, 2, 0, 1"}, expect = ACCEPTABLE, desc = "inbound next with error happens first")
	@Outcome(id = {
			"1, 0, 0, 1"}, expect = ACCEPTABLE, desc = "cancellation happened first")
	@Outcome(id = {"1, 3, 0, 1"}, expect = ACCEPTABLE, desc = "cancellation in between")
	@State
	public static class InboundNextLeadingToErrorAndOutboundCancelStressTest
			extends FluxSwitchOnFirstConditionalStressTest {

		static final RuntimeException DUMMY_ERROR = new RuntimeException("dummy");

		@Override
		Flux<String> switchOnFirst(Signal<? extends String> signal,
				Flux<String> inbound) {
			throw DUMMY_ERROR;
		}

		@Actor
		public void nextInbound() {
			main.tryOnNext("value");
		}

		@Actor
		public void cancelOutbound() {
			outboundSubscriber.cancel();
		}

		@Arbiter
		public void arbiter(LLLL_Result result) {
			result.r1 = inboundSubscription.cancelled ? 1 : 0;
			result.r2 =
					outboundSubscriber.onCompleteCalls.get() + outboundSubscriber.onErrorCalls.get() * 2 + outboundSubscriber.droppedErrors.size() * 3;
			result.r3 = outboundSubscriber.onNextCalls;
			result.r4 = outboundSubscriber.onNextDiscarded;

			if (outboundSubscriber.concurrentOnError.get()) {
				throw new RuntimeException("Concurrent OnError");
			}
			if (outboundSubscriber.concurrentOnNext.get()) {
				throw new RuntimeException("Concurrent OnNext");
			}
			if (outboundSubscriber.concurrentOnComplete.get()) {
				throw new RuntimeException("Concurrent OnComplete");
			}
		}
	}

	@JCStressTest
	@Outcome(id = {
			"1, 1, 2, 1, 1"}, expect = ACCEPTABLE, desc = "outbound cancel happened before inbound next")
	@Outcome(id = {
			"1, 1, 2, 2, 0"}, expect = ACCEPTABLE, desc = "inbound next happened before outbound cancel")
	@State
	public static class OutboundCancelAndInboundNextStressTest
			extends FluxSwitchOnFirstConditionalStressTest {

		Flux<String> inboundStream;

		@Override
		Flux<String> switchOnFirst(Signal<? extends String> signal,
				Flux<String> inbound) {
			return new Flux<String>() {
				@Override
				public void subscribe(CoreSubscriber<? super String> actual) {
					inboundStream = inbound;
					outboundSubscription.subscribe(actual);
				}
			};
		}

		{
			main.tryOnNext("value");
			inboundStream.subscribe(inboundSubscriber);
			inboundSubscriber.request(2);
		}

		@Actor
		public void nextInbound() {
			main.tryOnNext("value2");
		}

		@Actor
		public void cancelOutbound() {
			outboundSubscriber.cancel();
		}

		@Arbiter
		public void arbiter(LLLLL_Result result) {
			result.r1 = outboundSubscription.cancelled ? 1 : 0;
			result.r2 = inboundSubscription.cancelled ? 1 : 0;

			result.r3 =
					inboundSubscriber.onCompleteCalls.get() + inboundSubscriber.onErrorCalls.get() * 2 + outboundSubscriber.droppedErrors.size() * 3;
			result.r4 = inboundSubscriber.onNextCalls;
			result.r5 = outboundSubscriber.onNextDiscarded;

			if (inboundSubscriber.concurrentOnError.get()) {
				throw new RuntimeException("Concurrent OnError");
			}
			if (inboundSubscriber.concurrentOnNext.get()) {
				throw new RuntimeException("Concurrent OnNext");
			}
			if (inboundSubscriber.concurrentOnComplete.get()) {
				throw new RuntimeException("Concurrent OnComplete");
			}
		}
	}
}
