import java.util.*;

public class RoadNetwork {
	private List<Coord> coords;
	private Map<Coord, List<Coord>> paths;
	private int mapX;
	private int mapY;

	public RoadNetwork(List<Coord> coords, Map<Coord, List<Coord>> paths, int mapX, int mapY) {
		this.coords = coords;
		this.paths = paths;
		this.mapX = mapX;
		this.mapY = mapY;
	}

	public List<Coord> getCoords() {
		return coords;
	}

	public Map<Coord, List<Coord>> getPaths() {
		return paths;
	}

	public int getMapX() {
		return mapX;
	}

	public int getMapY() {
		return mapY;
	}
}
