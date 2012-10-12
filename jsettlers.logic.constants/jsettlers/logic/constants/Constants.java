package jsettlers.logic.constants;

/**
 * some constants of jsettlers.logics
 * 
 * @author Andreas Eberle
 * 
 */
public final class Constants {
	/**
	 * private constructor, because no instances of this class can be created.
	 */
	private Constants() {
	}

	public static final float TILE_PATHFINDER_COST = 1.0f;

	public static final byte STACK_SIZE = 8;

	public static final short WIDTH = 400;
	public static final short HEIGHT = 700;

	public static final int MAX_STONE_SIZE = 14;

	public static final byte MOVABLE_INTERRUPTS_PER_SECOND = 17;

	public static final short MOVABLE_INTERRUPT_DELAY = 1000 / MOVABLE_INTERRUPTS_PER_SECOND;

	public static float MOVABLE_STEP_DURATION = 0.4f;

	public static final float MOVABLE_TURN_PROBABILITY = 0.003F;
	public static final float MOVABLE_NO_ACTION_NEIGHBOR_PUSH_PROBABILITY = 0.2F;

	public static final float MOVABLE_TAKE_DROP_DURATION = 0.5f;

	public static final short MOVABLE_VIEW_DISTANCE = 8;

	public static final short SOLDIER_SEARCH_RADIUS = 30;
	public static final short TOWER_SEARCH_RADIUS = 45;
	public static final int BOWMAN_ATTACK_RADIUS = 20;

	/**
	 * interrupts until arrows are removed from the map again.<br>
	 * 50 seconds
	 */
	public static final short ARROW_DECOMPOSE_INTERRUPTS = 50 * MOVABLE_INTERRUPTS_PER_SECOND;

	public static final byte BRICKLAYER_ACTIONS_PER_MATERIAL = 12;

	public static final int TILES_PER_DIGGER = 15;

	public static final int PARTITION_MANANGER_RUNS_PER_TICK = 5;

	public static final float GHOST_PLAY_DURATION = 1;

	/**
	 * If the door is hit, its health is reduced by the hit strength / {@link #DOOR_HIT_RESISTENCY_FACTOR}
	 */
	public static final float DOOR_HIT_RESISTENCY_FACTOR = 2;

	public static final float TOWER_DOOR_REGENERATION = 0.01f;

}
