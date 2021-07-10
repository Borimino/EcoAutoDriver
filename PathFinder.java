import java.util.*;
import java.util.stream.*;

public class PathFinder {

	public static void main(String[] args) throws Exception {
		RoadNetwork roads = new MapInterpreter().getRoadNetwork();
		//System.out.println(findPaths(new Coord(1300, 460), new Coord(1160, 460), roads).get(0).path);
		//System.out.println(findPaths(new Coord(1300, 460), new Coord(1080, 460), roads).get(0).path);
		//System.out.println(findPaths(new Coord(1440, 460), new Coord(1520, 460), roads).get(0).path);
		//System.out.println(findPaths(new Coord(1540, 460), new Coord(1630, 460), roads).get(0).path);
		//System.out.println(findPaths(new Coord(1630, 460), new Coord(40, 460), roads).get(0).path);
		//System.out.println(findPaths(new Coord(1080, 460), new Coord(400, 460), roads).get(0).path);
		//System.out.println(findPaths(new Coord(820, 1380), new Coord(400, 460), roads).get(0).path);
		System.out.println(new PathFinder().findPaths(new Coord(1554, 108), new Coord(100, 1625), roads).get(0));
	}

	public List<Route> findPaths(Coord start, Coord end, RoadNetwork roads) {
		List<Coord> nearestStart = findNearest(start, roads);
		List<Coord> nearestEnd = findNearest(end, roads);

		List<Route> routes = new ArrayList<Route>();

		routes.add(findPath(nearestStart.get(0), nearestEnd.get(0), roads));
		routes.add(findPath(nearestStart.get(0), nearestEnd.get(1), roads));
		routes.add(findPath(nearestStart.get(0), nearestEnd.get(2), roads));
		routes.add(findPath(nearestStart.get(0), nearestEnd.get(3), roads));
		routes.add(findPath(nearestStart.get(1), nearestEnd.get(0), roads));
		routes.add(findPath(nearestStart.get(1), nearestEnd.get(1), roads));
		routes.add(findPath(nearestStart.get(1), nearestEnd.get(2), roads));
		routes.add(findPath(nearestStart.get(1), nearestEnd.get(3), roads));
		routes.add(findPath(nearestStart.get(2), nearestEnd.get(0), roads));
		routes.add(findPath(nearestStart.get(2), nearestEnd.get(1), roads));
		routes.add(findPath(nearestStart.get(2), nearestEnd.get(2), roads));
		routes.add(findPath(nearestStart.get(2), nearestEnd.get(3), roads));
		routes.add(findPath(nearestStart.get(3), nearestEnd.get(0), roads));
		routes.add(findPath(nearestStart.get(3), nearestEnd.get(1), roads));
		routes.add(findPath(nearestStart.get(3), nearestEnd.get(2), roads));
		routes.add(findPath(nearestStart.get(3), nearestEnd.get(3), roads));

		routes.removeIf(r -> r.path == null);

		routes.sort((r1, r2) -> {return r1.fScore - r2.fScore;});
		
		//System.out.println(routes);

		return routes;
	}


	public Route findPath(Coord start, Coord end, RoadNetwork roads) {
		//System.out.println("Finding path from " + start + " to " + end);

		Map<Coord, Integer> gScore = new HashMap();
		Map<Coord, Integer> fScore = new HashMap();

		PriorityQueue<Coord> open = new PriorityQueue(roads.getPaths().size(), (c1, c2) -> { return fScore.get(c1) - fScore.get(c2); });
		open.add(start);

		Map<Coord, Coord> cameFrom = new HashMap<>();

		gScore.put(start, 0);
		fScore.put(start, distanceBetween(start, end, roads));

		while (open.peek() != null) {
			Coord current = open.poll();
			if (current == end) {
				//System.out.println("Path found");
				return new Route(reconstructPath(cameFrom, current), fScore.get(current));
			}

			for (Coord neighbor : getNeighborsForCoord(current, roads)) {
				//System.out.println("Neighbor " + neighbor);

				int tentative_gScore = gScore.get(current) + distanceBetween(current, neighbor, roads);
				if (!gScore.containsKey(neighbor) || tentative_gScore < gScore.get(neighbor)) {
					cameFrom.put(neighbor, current);
					gScore.put(neighbor, tentative_gScore);
					fScore.put(neighbor, gScore.get(neighbor) + distanceBetween(neighbor, end, roads));
					if (!open.contains(neighbor)) {
						open.add(neighbor);
					}
				}
			}
		}

		return new Route(null, Integer.MAX_VALUE);
	}

	private List<Coord> getNeighborsForCoord(Coord current, RoadNetwork roads) {
		int currentIndex = roads.getCoords().indexOf(current);
		//System.out.println("Current index " + currentIndex);

		List<Coord> neighbors = roads.getPaths().get(current);
		if (neighbors == null) {
			return new ArrayList<>();
		}
		return neighbors;
	}

	private List<Coord> reconstructPath(Map<Coord, Coord> cameFrom, Coord current) {
		List<Coord> result = new ArrayList<>();
		while (current != null) {
			result.add(current);
			current = cameFrom.get(current);
		}
		Collections.reverse(result);
		return result;
	}

	private List<Coord> findNearest(Coord point, RoadNetwork roads) {
		List<Coord> coords = new ArrayList<>(roads.getCoords());
		coords.sort((c1, c2) -> {return distanceBetween(c1, point, roads) - distanceBetween(c2, point, roads);});
		return coords;
	}

	private int distanceBetween(Coord start, Coord end, RoadNetwork roads) {
		double minDist = Math.sqrt((start.x-end.x)*(start.x-end.x) + (start.y-end.y)*(start.y-end.y));

		List<Coord> toTests = Arrays.asList(
			new Coord(start.x+roads.getMapX(), start.y),
			new Coord(start.x+roads.getMapX(), start.y+roads.getMapY()),
			new Coord(start.x, start.y+roads.getMapY()),
			new Coord(start.x-roads.getMapX(), start.y+roads.getMapY()),
			new Coord(start.x-roads.getMapX(), start.y),
			new Coord(start.x-roads.getMapX(), start.y-roads.getMapY()),
			new Coord(start.x, start.y-roads.getMapY()),
			new Coord(start.x+roads.getMapX(), start.y-roads.getMapY())
		);

		for (Coord toTest : toTests) {
			double tmpDist = Math.sqrt((toTest.x-end.x)*(toTest.x-end.x) + (toTest.y-end.y)*(toTest.y-end.y));
			if (tmpDist < minDist) {
				minDist = tmpDist;
			}
		}

		return (int) minDist;
	}

	public static class Route {
		public int fScore;
		public List<Coord> path;

		public Route(List<Coord> path, int fScore) {
			this.path = path;
			this.fScore = fScore;
		}

		public String toString() {
			return "{ fScore: " + fScore + ", path: " + path + "}\n";
		}


	}

}

