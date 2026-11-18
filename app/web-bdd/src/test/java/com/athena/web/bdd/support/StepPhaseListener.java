package com.athena.web.bdd.support;

import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.TestCaseStarted;
import io.cucumber.plugin.event.TestStepStarted;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 在每個場景的前置步驟全部走完、第一個「當」或「那麼」步驟開始之前，通知 {@link WhenStart} 記下那一刻的樣子。
 *
 * <p>規格有幾句結果句要比「這個案例的 When 之前」與現在，例如「後端沒有任何資料被改動」。
 * Cucumber 的步驟掛勾拿不到目前是哪一種步驟，所以改用外掛聽步驟開始的事件，看步驟的關鍵字：
 * 「而且」「並且」「同時」「但是」沿用前一步的種類，第一個不是「假設」類的步驟一開始就通知一次。
 * 事件是在執行步驟的那條執行緒上、步驟真的開始跑之前送來的，所以通知時畫面與後端都還是前置剛做完的樣子。
 *
 * <p>另外在進出「那麼」步驟時通知一次，讓 {@link WhenStart} 不把結果句自己為了看畫面而做的點按
 * （例如打開側欄看徽章）算成使用者按的。
 *
 * <p>外掛與步驟類別不在同一個物件容器裡，拿不到這個場景的 {@link WhenStart}；
 * 所以由 {@link WhenStart} 在場景開始時把要做的事登記在這條執行緒上，場景結束時拿掉。
 * 沒有登記時（例如只檢查步驟有沒有定義的試跑，掛勾不會執行）什麼都不做。
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

    /** 登記這條執行緒上目前這個場景要在離開前置時做的事。 */
    static void onLeavingGiven(Runnable action) {
        ON_LEAVING_GIVEN.set(action);
    }

    /**
     * 登記這條執行緒上目前這個場景在進出「那麼」時要做的事：進入時收到 {@code true}，回到「假設」或「當」時收到 {@code false}。
     * 「而且」這類連接詞沿用前一步的種類，不會通知。進入時的通知排在離開前置的那一次之後。
     */
    static void onThenPhase(Consumer<Boolean> action) {
        ON_THEN_PHASE.set(action);
    }

    /**
     * 這條執行緒上目前這個場景有沒有用到這個步驟定義，{@code pattern} 是步驟定義註記上的那一句。
     * 有些「當」開始那一刻的紀錄只有特定結果句用得到，而且只在那些場景的畫面上才記得到，給它們決定要不要登記。
     */
    public static boolean currentScenarioUses(String pattern) {
        return STEP_PATTERNS.get().contains(pattern);
    }

    static void clear() {
        ON_LEAVING_GIVEN.remove();
        ON_THEN_PHASE.remove();
    }

    @Override
    public void setEventPublisher(EventPublisher publisher) {
        publisher.registerHandlerFor(TestCaseStarted.class, event -> {
            STILL_IN_GIVEN.set(true);
            IN_THEN.set(false);
            STEP_PATTERNS.set(event.getTestCase().getTestSteps().stream()
                    .filter(PickleStepTestStep.class::isInstance)
                    .map(step -> ((PickleStepTestStep) step).getPattern())
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
