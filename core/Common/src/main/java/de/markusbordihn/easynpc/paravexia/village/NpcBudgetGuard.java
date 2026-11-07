package de.markusbordihn.easynpc.paravexia.village;

/**
 * Watches the Easy NPC tick time and raises a degrade level when the rolling average is above the
 * budget. Levels go up one at a time (at most once per window) and come down one at a time after
 * the average stayed below the recover threshold for the recover period.
 *
 * <p>Level 1 limits walkers to one per village, level 2 keeps only villages with a player close by
 * awake, level 3 also sends extras of awake villages to sleep.
 */
public final class NpcBudgetGuard {

  public static final int MAX_LEVEL = 3;

  private BudgetSettings settings;
  private long[] samples;
  private int sampleIndex;
  private int sampleCount;
  private long sampleSum;
  private int level;
  private int ticksSinceRaise;
  private int ticksBelowRecover;

  public NpcBudgetGuard(BudgetSettings settings) {
    this.configure(settings);
  }

  public void configure(BudgetSettings settings) {
    this.settings = settings;
    this.samples = new long[Math.max(1, settings.windowTicks())];
    this.sampleIndex = 0;
    this.sampleCount = 0;
    this.sampleSum = 0;
    this.ticksSinceRaise = 0;
    this.ticksBelowRecover = 0;
  }

  /**
   * Adds the Easy NPC tick time of one server tick.
   *
   * @return the level change: +1, -1 or 0
   */
  public int tick(long npcNanos) {
    this.sampleSum -= this.samples[this.sampleIndex];
    this.samples[this.sampleIndex] = npcNanos;
    this.sampleSum += npcNanos;
    this.sampleIndex = (this.sampleIndex + 1) % this.samples.length;
    if (this.sampleCount < this.samples.length) {
      this.sampleCount++;
    }
    this.ticksSinceRaise++;

    if (this.sampleCount < this.samples.length) {
      return 0;
    }

    long average = this.averageNanos();
    if (average > this.settings.budgetNanos()) {
      this.ticksBelowRecover = 0;
      if (this.level < MAX_LEVEL && this.ticksSinceRaise >= this.samples.length) {
        this.level++;
        this.ticksSinceRaise = 0;
        return 1;
      }
      return 0;
    }

    if (average < this.settings.budgetNanos() * this.settings.recoverPercent() / 100) {
      if (this.level > 0 && ++this.ticksBelowRecover >= this.settings.recoverTicks()) {
        this.level--;
        this.ticksBelowRecover = 0;
        return -1;
      }
    } else {
      this.ticksBelowRecover = 0;
    }
    return 0;
  }

  public long averageNanos() {
    return this.sampleCount == 0 ? 0 : this.sampleSum / this.sampleCount;
  }

  public int level() {
    return this.level;
  }

  public void reset() {
    this.level = 0;
    this.configure(this.settings);
  }
}
