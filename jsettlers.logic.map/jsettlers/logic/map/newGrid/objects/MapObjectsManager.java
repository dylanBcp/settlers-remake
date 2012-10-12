package jsettlers.logic.map.newGrid.objects;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.PriorityQueue;

import jsettlers.common.landscape.ELandscapeType;
import jsettlers.common.landscape.EResourceType;
import jsettlers.common.map.shapes.MapNeighboursArea;
import jsettlers.common.map.shapes.MapShapeFilter;
import jsettlers.common.mapobject.EMapObjectType;
import jsettlers.common.mapobject.IAttackableTowerMapObject;
import jsettlers.common.material.EMaterialType;
import jsettlers.common.material.ESearchType;
import jsettlers.common.position.RelativePoint;
import jsettlers.common.position.ShortPoint2D;
import jsettlers.logic.constants.Constants;
import jsettlers.logic.objects.PigObject;
import jsettlers.logic.objects.RessourceSignMapObject;
import jsettlers.logic.objects.SelfDeletingMapObject;
import jsettlers.logic.objects.SoundableSelfDeletingObject;
import jsettlers.logic.objects.StandardMapObject;
import jsettlers.logic.objects.arrow.ArrowObject;
import jsettlers.logic.objects.building.BuildingWorkAreaMarkObject;
import jsettlers.logic.objects.building.ConstructionMarkObject;
import jsettlers.logic.objects.corn.Corn;
import jsettlers.logic.objects.stack.StackMapObject;
import jsettlers.logic.objects.stone.Stone;
import jsettlers.logic.objects.tree.AdultTree;
import jsettlers.logic.objects.tree.Tree;
import jsettlers.logic.timer.ITimerable;
import jsettlers.logic.timer.Timer100Milli;
import random.RandomSingleton;
import synchronic.timer.NetworkTimer;

/**
 * This class manages the MapObjects on the grid. It handles timed events like growth interrupts of a tree or deletion of arrows.
 * 
 * @author Andreas Eberle
 * 
 */
public final class MapObjectsManager implements ITimerable, Serializable {
	private static final long serialVersionUID = 1833055351956872224L;

	private final IMapObjectsManagerGrid grid;
	private final PriorityQueue<TimeEvent> timingQueue = new PriorityQueue<TimeEvent>();

	public MapObjectsManager(IMapObjectsManagerGrid grid) {
		this.grid = grid;
		Timer100Milli.add(this);
	}

	private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
		ois.defaultReadObject();
		Timer100Milli.add(this);
	}

	@Override
	public void timerEvent() {
		int gameTime = NetworkTimer.getGameTime();

		TimeEvent curr = null;
		curr = timingQueue.peek();
		while (curr != null && curr.isOutDated(gameTime)) {
			timingQueue.poll();
			if (curr.shouldRemoveObject()) {
				removeMapObject(curr.mapObject.getX(), curr.mapObject.getY(), curr.mapObject);
			} else {
				curr.getMapObject().changeState();
			}

			curr = timingQueue.peek();
		}

	}

	@Override
	public void kill() {
		Timer100Milli.remove(this);
	}

	public boolean executeSearchType(ShortPoint2D pos, ESearchType type) {
		switch (type) {
		case CUTTABLE_TREE:
			return cutTree(pos);

		case CUTTABLE_STONE:
			cutStone(pos);
			return true;

		case PLANTABLE_TREE:
			return plantTree(new ShortPoint2D(pos.getX(), pos.getY() + 1));

		case CUTTABLE_CORN:
			return cutCorn(pos);

		case PLANTABLE_CORN:
			return plantCorn(pos);

		case RESOURCE_SIGNABLE:
			return addRessourceSign(pos);

		default:
			System.err.println("can't handle search type in executeSearchType(): " + type);
			return false;
		}
	}

	private boolean addRessourceSign(ShortPoint2D pos) {
		EResourceType resourceType = grid.getRessourceTypeAt(pos.getX(), pos.getY());
		byte resourceAmount = grid.getRessourceAmountAt(pos.getX(), pos.getY());

		RessourceSignMapObject object = new RessourceSignMapObject(pos, resourceType, resourceAmount / ((float) Byte.MAX_VALUE));
		addMapObject(pos, object);
		timingQueue.add(new TimeEvent(object, RessourceSignMapObject.getLivetime(), true));

		return true;
	}

	private void cutStone(ShortPoint2D pos) {
		short x = (short) (pos.getX() - 1);
		short y = (short) (pos.getY() + 1);
		AbstractHexMapObject stone = grid.getMapObject(x, y, EMapObjectType.STONE);

		if (stone != null) {
			stone.cutOff();

			if (!stone.canBeCut()) {
				addSelfDeletingMapObject(pos, EMapObjectType.CUT_OFF_STONE, Stone.DECOMPOSE_DELAY, (byte) -1);
				removeMapObjectType(x, y, EMapObjectType.STONE);
			}
		}
	}

	private boolean plantTree(ShortPoint2D pos) {
		Tree tree = new Tree(pos);
		addMapObject(pos, tree);
		timingQueue.offer(new TimeEvent(tree, Tree.GROWTH_DURATION, false));
		return true;
	}

	private boolean plantCorn(ShortPoint2D pos) {
		grid.setLandscape(pos.getX(), pos.getY(), ELandscapeType.EARTH);
		for (ShortPoint2D curr : new MapShapeFilter(new MapNeighboursArea(pos), grid.getWidth(), grid.getHeight())) {
			grid.setLandscape(curr.getX(), curr.getY(), ELandscapeType.EARTH);
		}
		Corn corn = new Corn(pos);
		addMapObject(pos, corn);
		timingQueue.offer(new TimeEvent(corn, Corn.GROWTH_DURATION, false));
		timingQueue.offer(new TimeEvent(corn, Corn.GROWTH_DURATION + Corn.DECOMPOSE_DURATION, false));
		timingQueue.offer(new TimeEvent(corn, Corn.GROWTH_DURATION + Corn.DECOMPOSE_DURATION + Corn.REMOVE_DURATION, true));
		return true;
	}

	private boolean cutCorn(ShortPoint2D pos) {
		short x = pos.getX();
		short y = pos.getY();
		if (grid.isInBounds(x, y)) {
			AbstractObjectsManagerObject corn = (AbstractObjectsManagerObject) grid.getMapObject(x, y, EMapObjectType.CORN_ADULT);
			if (corn != null && corn.cutOff()) {
				timingQueue.offer(new TimeEvent(corn, Corn.REMOVE_DURATION, true));
				return true;
			}
		}
		return false;
	}

	private boolean cutTree(ShortPoint2D pos) {
		short x = (short) (pos.getX() - 1);
		short y = (short) (pos.getY() - 1);
		if (grid.isInBounds(x, y)) {
			AbstractObjectsManagerObject tree = (AbstractObjectsManagerObject) grid.getMapObject(x, y, EMapObjectType.TREE_ADULT);
			if (tree != null && tree.cutOff()) {
				timingQueue.offer(new TimeEvent(tree, Tree.DECOMPOSE_DURATION, true));
				return true;
			}
		}
		return false;
	}

	private final boolean addMapObject(ShortPoint2D pos, AbstractHexMapObject mapObject) {
		return addMapObject(pos.getX(), pos.getY(), mapObject);
	}

	private final boolean addMapObject(short x, short y, AbstractHexMapObject mapObject) {
		for (RelativePoint point : mapObject.getBlockedTiles()) {
			short currX = point.calculateX(x);
			short currY = point.calculateY(y);
			if (!grid.isInBounds(currX, currY) || grid.isBlocked(currX, currY)) {
				return false;
			}
		}

		grid.addMapObject(x, y, mapObject);

		setBlockedForObject(x, y, mapObject, true);
		return true;
	}

	public void removeMapObjectType(short x, short y, EMapObjectType mapObjectType) {
		AbstractHexMapObject removed = grid.removeMapObjectType(x, y, mapObjectType);

		if (removed != null) {
			setBlockedForObject(x, y, removed, false);
		}
	}

	public void removeMapObject(short x, short y, AbstractHexMapObject mapObject) {
		boolean removed = grid.removeMapObject(x, y, mapObject);

		if (removed) {
			setBlockedForObject(x, y, mapObject, false);
		}
	}

	private void setBlockedForObject(short oldX, short oldY, AbstractHexMapObject mapObject, boolean blocked) {
		for (RelativePoint point : mapObject.getBlockedTiles()) {
			short newX = point.calculateX(oldX);
			short newY = point.calculateY(oldY);
			if (grid.isInBounds(newX, newY)) {
				grid.setBlocked(newX, newY, blocked);
			}
		}
	}

	public void addStone(ShortPoint2D pos, int capacity) {
		addMapObject(pos, new Stone(capacity));
	}

	public void plantAdultTree(ShortPoint2D pos) {
		addMapObject(pos, new AdultTree(pos));
	}

	/**
	 * Adds an arrow object to the map flying from
	 * 
	 * @param attackedPos
	 *            Attacked position.
	 * @param shooterPos
	 *            Position of the shooter.
	 * @param hitStrength
	 *            Strength of the hit.
	 */
	public void addArrowObject(ShortPoint2D attackedPos, ShortPoint2D shooterPos, byte shooterPlayer, float hitStrength) {
		ArrowObject arrow = new ArrowObject(grid, attackedPos, shooterPos, shooterPlayer, hitStrength);
		addMapObject(attackedPos, arrow);
		timingQueue.offer(new TimeEvent(arrow, arrow.getEndTime(), false));
		timingQueue.offer(new TimeEvent(arrow, arrow.getEndTime() + ArrowObject.MIN_DECOMPOSE_DELAY * (1 + RandomSingleton.nextF()), true));
	}

	public void addSimpleMapObject(ShortPoint2D pos, EMapObjectType objectType, boolean blocking, byte player) {
		addMapObject(pos, new StandardMapObject(objectType, blocking, player));
	}

	public void addBuildingWorkAreaObject(ShortPoint2D pos, float radius) {
		addMapObject(pos, new BuildingWorkAreaMarkObject(radius));
	}

	public void addSelfDeletingMapObject(ShortPoint2D pos, EMapObjectType mapObjectType, float duration, byte player) {
		SelfDeletingMapObject object;
		switch (mapObjectType) {
		case GHOST:
		case BUILDING_DECONSTRUCTION_SMOKE:
			object = new SoundableSelfDeletingObject(pos, mapObjectType, duration, player);
			break;
		default:
			object = new SelfDeletingMapObject(pos, mapObjectType, duration, player);
			break;
		}
		addMapObject(pos, object);
		timingQueue.add(new TimeEvent(object, duration, true));
	}

	public void setConstructionMarking(short x, short y, byte value) {
		if (value >= 0) {
			ConstructionMarkObject markObject = (ConstructionMarkObject) grid.getMapObject(x, y, EMapObjectType.CONSTRUCTION_MARK);
			if (markObject == null) {
				addMapObject(x, y, new ConstructionMarkObject(value));
			} else {
				markObject.setConstructionValue(value);
			}
		} else {
			removeMapObjectType(x, y, EMapObjectType.CONSTRUCTION_MARK);
		}
	}

	public boolean canPush(ShortPoint2D position) {
		StackMapObject stackObject = (StackMapObject) grid.getMapObject(position.getX(), position.getY(), EMapObjectType.STACK_OBJECT);
		int sum = 0;

		while (stackObject != null) { // find correct stack
			sum += stackObject.getSize();

			stackObject = getNextStackObject(stackObject);
		}

		return sum < Constants.STACK_SIZE;
	}

	public final boolean canPop(short x, short y, EMaterialType materialType) {
		StackMapObject stackObject = getStackAtPosition(x, y, materialType);

		return stackObject != null && (stackObject.getMaterialType() == materialType || materialType == null) && !stackObject.isEmpty();
	}

	public final void markStolen(short x, short y, boolean mark) {
		if (mark) {
			StackMapObject stack = getStackAtPosition(x, y, null);
			while (stack != null) {
				if (stack.hasUnstolen()) {
					stack.incrementStolenMarks();
					break;
				}

				stack = getNextStackObject(stack);
			}
		} else {
			StackMapObject stack = getStackAtPosition(x, y, null);
			while (stack != null) {
				if (stack.hasStolenMarks()) {
					stack.decrementStolenMarks();
					break;
				}

				stack = getNextStackObject(stack);
			}
		}
	}

	private final static StackMapObject getNextStackObject(StackMapObject stack) {
		AbstractHexMapObject next = stack.getNextObject();
		if (next != null) {
			return (StackMapObject) next.getMapObject(EMapObjectType.STACK_OBJECT);
		} else {
			return null;
		}
	}

	public boolean pushMaterial(short x, short y, EMaterialType materialType) {
		assert materialType != null : "material type can never be null here";

		StackMapObject stackObject = getStackAtPosition(x, y, materialType);

		if (stackObject == null) {
			grid.addMapObject(x, y, new StackMapObject(materialType, (byte) 1));
			grid.setProtected(x, y, true);
			return true;
		} else {
			if (stackObject.isFull()) {
				return false;
			} else {
				stackObject.increment();
				return true;
			}
		}
	}

	public final boolean popMaterial(short x, short y, EMaterialType materialType) {
		return popMaterialTypeAt(x, y, materialType) != null;
	}

	private EMaterialType popMaterialTypeAt(short x, short y, EMaterialType materialType) {
		StackMapObject stackObject = getStackAtPosition(x, y, materialType);

		if (stackObject == null) {
			return null;
		} else {
			if (stackObject.isEmpty()) {
				removeStackObject(x, y, stackObject);
				return null;
			} else {
				stackObject.decrement();
				if (stackObject.isEmpty()) { // remove empty stack object
					removeStackObject(x, y, stackObject);
				}
				return stackObject.getMaterialType();
			}
		}
	}

	public final EMaterialType getMaterialTypeAt(short x, short y) {
		StackMapObject stackObject = (StackMapObject) grid.getMapObject(x, y, EMapObjectType.STACK_OBJECT);

		if (stackObject == null) {
			return null;
		} else {
			return stackObject.getMaterialType();
		}
	}

	private final void removeStackObject(short x, short y, StackMapObject stackObject) {
		removeMapObject(x, y, stackObject);
		if (grid.getMapObject(x, y, EMapObjectType.STACK_OBJECT) == null) {
			grid.setProtected(x, y, false); // no other stack, so remove protected
		}
	}

	public final byte getStackSize(short x, short y, EMaterialType materialType) {
		StackMapObject stackObject = getStackAtPosition(x, y, materialType);
		if (stackObject == null) {
			return 0;
		} else {
			return stackObject.getSize();
		}
	}

	public final boolean hasStealableMaterial(short x, short y) {
		StackMapObject stackObject = (StackMapObject) grid.getMapObject(x, y, EMapObjectType.STACK_OBJECT);

		while (stackObject != null) { // find all stacks
			if (stackObject.hasUnstolen()) {
				return true;
			}

			stackObject = getNextStackObject(stackObject);
		}

		return false;
	}

	public final EMaterialType stealMaterialAt(short x, short y) {
		return popMaterialTypeAt(x, y, null);
	}

	private StackMapObject getStackAtPosition(short x, short y, EMaterialType materialType) {
		StackMapObject stackObject = (StackMapObject) grid.getMapObject(x, y, EMapObjectType.STACK_OBJECT);

		while (stackObject != null && stackObject.getMaterialType() != materialType && materialType != null) { // find correct stack
			stackObject = getNextStackObject(stackObject);
		}
		return stackObject;
	}

	public void addBuildingTo(ShortPoint2D position, AbstractHexMapObject newBuilding) {
		addMapObject(position, newBuilding);
	}

	public void placePig(ShortPoint2D pos, boolean place) {
		if (place) {
			AbstractHexMapObject pig = grid.getMapObject(pos.getX(), pos.getY(), EMapObjectType.PIG);
			if (pig == null) {
				addMapObject(pos, new PigObject());
			}
		} else {
			removeMapObjectType(pos.getX(), pos.getY(), EMapObjectType.PIG);
		}
	}

	public boolean isPigThere(ShortPoint2D pos) {
		AbstractHexMapObject pig = grid.getMapObject(pos.getX(), pos.getY(), EMapObjectType.PIG);
		return pig != null;
	}

	public boolean isPigAdult(ShortPoint2D pos) {
		AbstractHexMapObject pig = grid.getMapObject(pos.getX(), pos.getY(), EMapObjectType.PIG);
		return pig != null && pig.canBeCut();
	}

	public void addWaves(short x, short y) {
		grid.addMapObject(x, y, new DecorationMapObject(EMapObjectType.WAVES));
	}

	public void addFish(short x, short y) {
		grid.addMapObject(x, y, new DecorationMapObject(EMapObjectType.FISH_DECORATION));
	}

	private static class TimeEvent implements Comparable<TimeEvent>, Serializable {
		private static final long serialVersionUID = -4439126418530597713L;

		private final AbstractObjectsManagerObject mapObject;
		private final int eventTime;
		private final boolean shouldRemove;

		/**
		 * 
		 * @param mapObject
		 * @param duration
		 *            in seconds
		 * @param shouldRemove
		 *            if true, the map object will be removed after this event
		 */
		protected TimeEvent(AbstractObjectsManagerObject mapObject, float duration, boolean shouldRemove) {
			this.mapObject = mapObject;
			this.shouldRemove = shouldRemove;
			this.eventTime = (int) (NetworkTimer.getGameTime() + duration * 1000);
		}

		public boolean isOutDated(int gameTime) {
			return gameTime > eventTime;
		}

		private AbstractObjectsManagerObject getMapObject() {
			return mapObject;
		}

		public boolean shouldRemoveObject() {
			return shouldRemove;
		}

		@Override
		public int compareTo(TimeEvent o) {
			return this.eventTime - o.eventTime;
		}
	}

	/**
	 * Adds an attackable tower map object to the grid.
	 * 
	 * @param position
	 *            Position the map object will be added.
	 * @param attackableTowerMapObject
	 *            The object to be added. NOTE: This object must be an instance of {@link IAttackableTowerMapObject}!
	 */
	public void addAttackableTowerObject(ShortPoint2D position, AbstractHexMapObject attackableTowerMapObject) {
		assert attackableTowerMapObject instanceof IAttackableTowerMapObject;

		this.addMapObject(position, attackableTowerMapObject);
	}

}
