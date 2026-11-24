package com.athena.web.bdd.support;

import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.TestCaseStarted;
import io.cucumber.plugin.event.TestStepStarted;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Cucumber 的步驟掛勾拿不到目前是哪一種步驟，所以改用外掛聽步驟開始的事件。
 * 事件是在執行步驟的那條執行緒上、步驟真的開始跑之前送來的。
 *
 * <p>外掛與步驟類別不在同一個物件容器裡，拿不到這個場景的 {@link WhenStart}；
 * 所以由 {@link WhenStart} 在場景開始時把要做的事登記在這條執行緒上，場景結束時拿掉。
 */
public final class StepPhaseListener implements ConcurrentEventListener {

    private static final Set<String> GIVEN = Set.of("假設", "假定", "假如", "Given");

    private static final Set<String> WHEN_OR_THEN = Set.of("當", "那麼", "When", "Then");

    private static final Set<String> WHEN = Set.of("當", "When");

    private static final Set<String> THEN = Set.of("那麼", "Then");

    private static final ThreadLocal<Runnable> ON_LEAVING_GIVEN = new ThreadLocal<>();

    private static final ThreadLocal<Consumer<Boolean>> ON_THEN_PHASE = new ThreadLocal<>();

    private static final ThreadLocal<Boolean> STILL_IN_GIVEN = ThreadLocal.withInitial(() -> true);

    private static final ThreadLocal<Boolean> IN_THEN = ThreadLocal.withInitial(() -> false);

    private static final ThreadLocal<Set<String>> STEP_PATTERNS = ThreadLocal.withInitial(Set::of);

    private static volatile boolean installed;

    /**
     * 命令列另給 {@code cucumber.plugin} 會整個蓋掉 runner 上的外掛清單，這個外掛就跟著消失，
     * 要和 When 之前比的結果句會在場景跑到一半才莫名失敗；所以場景一開始就檢查並說清楚怎麼修。
     */
    static void requireInstalled() {
        if (!installed) {
            throw new IllegalStateException("沒有掛上 " + StepPhaseListener.class.getName()
                    + " 外掛：命令列指定 cucumber.plugin 時要把它一起列進去，例如 -Dcucumber.plugin=\"pretty, "
                    + StepPhaseListener.class.getName() + "\"");
        }
    }

    static void onLeavingGiven(Runnable action) {
        ON_LEAVING_GIVEN.set(action);
    }

    static void onThenPhase(Consumer<Boolean> action) {
        ON_THEN_PHASE.set(action);
    }

    public static boolean currentScenarioUses(String pattern) {
        return STEP_PATTERNS.get().contains(pattern);
    }

    static void clear() {
        ON_LEAVING_GIVEN.remove();
        ON_THEN_PHASE.remove();
    }

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        installed = true;
        publisher.registerHandlerFor(TestCaseStarted.class, event -> {
            STILL_IN_GIVEN.set(true);
            IN_THEN.set(false);
            STEP_PATTERNS.set(event.getTestCase().getTestSteps().stream()
                    .filter(PickleStepTestStep.class::isInstance)
                    .map(step -> ((PickleStepTestStep) step).getPattern())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toUnmodifiableSet()));
        });
        publisher.registerHandlerFor(TestStepStarted.class, this::onStepStarted);
    }

    private void onStepStarted(TestStepStarted event) {
        if (!(event.getTestStep() instanceof PickleStepTestStep step)) {
            return;
        }
        String keyword = step.getStep().getKeyword().strip();
        if (STILL_IN_GIVEN.get() && !GIVEN.contains(keyword) && WHEN_OR_THEN.contains(keyword)) {
            STILL_IN_GIVEN.set(false);
            Runnable action = ON_LEAVING_GIVEN.get();
            if (action != null) {
                action.run();
            }
        }
        Boolean inThen = THEN.contains(keyword) ? Boolean.TRUE
                : GIVEN.contains(keyword) || WHEN.contains(keyword) ? Boolean.FALSE : null;
        if (inThen == null || inThen.equals(IN_THEN.get())) {
            return;
        }
        IN_THEN.set(inThen);
        Consumer<Boolean> action = ON_THEN_PHASE.get();
        if (action != null) {
            action.accept(inThen);
        }
    }
}
