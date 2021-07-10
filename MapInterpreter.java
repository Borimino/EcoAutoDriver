import java.io.*;
import java.util.*;
import java.util.stream.*;
import java.util.regex.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class MapInterpreter {
	private static String binMapFileName = "binaryMap";
	private static String baseUrl = "http://51.81.116.134:3081/";
	private static String mapName = "Government Infrastructure";
	private static String[] districtNames = {"Finished Roads", "Budgeted Roads", "Budgeted Stone Road"};

	private static String districtUrlExtension = "api/v1/laws/districtmap/";
	private static String sizeUrlExtension = "api/v1/map/dimension/";

	private int[] wantedDistricts;
	public int mapSizeX;
	public int mapSizeZ;
	public int fullMapSizeX;
	public int fullMapSizeZ;

	private static int CHUNK_SIZE = 5;
	private static int SLIM_OFFSET_MIN = 3;
	private static int SLIM_OFFSET_MAX = 1;
	private static int WIDE_OFFSET_MIN = 4;
	private static int WIDE_OFFSET_MAX = 0;

	public static void main(String[] args) throws Exception {
		new MapInterpreter().getRoadNetwork();
	}

	public RoadNetwork getRoadNetwork() throws Exception {
		URL url = new URL(baseUrl + districtUrlExtension + URLEncoder.encode(mapName, StandardCharsets.UTF_8.toString()).replace("+", "%20"));
		String response = getContentFromURL(url);

		wantedDistricts = new int[districtNames.length];
		for (int i = 0; i < districtNames.length; i++) { 
			String districtName = districtNames[i];
			Pattern pId = Pattern.compile(".*\"ID\":([^,]*),\"Name\":\"" + districtName + ".*");
			Matcher mId = pId.matcher(response);
			boolean bId = mId.matches();
			wantedDistricts[i] = Integer.parseInt(mId.group(1));
		}
		//System.out.println(wantedDistricts);

		Pattern p = Pattern.compile(".*\"DistrictMap\":\"([^\"]*)\".*");
		Matcher m = p.matcher(response);
		boolean b = m.matches();
		String b64 = m.group(1);
		byte[] hexMap = Base64.getDecoder().decode(b64);
		//System.out.println("Hex map length: " + hexMap.length);

		URL url2 = new URL(baseUrl + sizeUrlExtension);
		String response2 = getContentFromURL(url2);

		Pattern px = Pattern.compile(".*\"x\":([0-9]+).*");
		Pattern pz = Pattern.compile(".*\"z\":([0-9]+).*");
		Matcher mx = px.matcher(response2);
		Matcher mz = pz.matcher(response2);
		boolean bx = mx.matches();
		boolean bz = mz.matches();
		fullMapSizeX = Integer.parseInt(mx.group(1));
		fullMapSizeZ = Integer.parseInt(mz.group(1));
		mapSizeX = (int) fullMapSizeX/5;
		mapSizeZ = (int) fullMapSizeZ/5;


		boolean[][] binMap = new boolean[mapSizeX][mapSizeZ];
		for (int z = 0; z < mapSizeZ; z++) {
			for (int x = 0; x < mapSizeX; x++) {
				int hexValue = (((int) hexMap[x + z*mapSizeX]) + 256) % 256;

				//System.out.print(hexValue + " ");
				for (int i = 0; i < wantedDistricts.length; i++) {
					if (wantedDistricts[i] % 256 == hexValue) {
						binMap[z][x] = true;
						break;
						//System.out.print("1");
					} else {
						binMap[z][x] = false;
						//System.out.print("0");
					}
				}
			}
			//System.out.println();
		}

		//System.out.println("Bin map length: " + binMap.length);
		//System.out.println("Bin map 0 length: " + binMap[0].length);

		List<Rect> rectMap = buildRectMap(binMap);

		if (rectMap.size() == 0) {
			throw new RuntimeException("No roads found in district");
		}

		//drawRectMap(rectMap);

		//System.out.println("Rect map: " + rectMap);

		List<Rect> oldRectMap = rectMap;
		List<Rect> newRectMap = new ArrayList<>();
		int splitCount = 0;
		boolean splitHappened = true;
		while (splitHappened) {
		//for (int i = 0; i < 50; i++) {
			newRectMap = splitRects(oldRectMap);
			splitHappened = !(oldRectMap.equals(newRectMap));

			if (splitCount % 100 == 0) {
				//System.out.println("Test " + splitCount);
			}
			splitCount += 1;

			oldRectMap = newRectMap;
		}
		//System.out.println("Split rect map: " + newRectMap);
		//System.out.println("Split rect map size: " + newRectMap.size());

		//drawRectMap(newRectMap);

		boolean mergeHappened = true;
		int mergeCount = 0;
		while (mergeHappened) {
			newRectMap = mergeRects(oldRectMap);
			mergeHappened = !(oldRectMap.equals(newRectMap));

			if (mergeCount % 1 == 0) {
				//System.out.println("Test " + mergeCount);
			}

			mergeCount++;

			oldRectMap = newRectMap;
		}
		//System.out.println("Merged rect map: " + newRectMap);
		//System.out.println("Merged rect map size: " + newRectMap.size());

		//drawRectMap(newRectMap);

		boolean longSplitHappened = true;
		while (longSplitHappened) {
			newRectMap = longSplitRects(oldRectMap);
			longSplitHappened = !(oldRectMap.equals(newRectMap));

			oldRectMap = newRectMap;
		}
		//System.out.println("Long split rect map: " + newRectMap);
		//System.out.println("Long split rect map size: " + newRectMap.size());

		//drawRectMap(newRectMap);

		int intersectionCount = 0;
		int wideRoadCount = 0;
		int slimRoadCount = 0;
		int pathCount = 0;
		for (Rect rect : newRectMap) {
			if ((rect.max_x - rect.min_x) > 2 && (rect.max_y - rect.min_y) > 2) {
				System.out.println("Large rect: " + rect);
			}
			if ((rect.max_x - rect.min_x) <= 2 && (rect.max_y - rect.min_y) <= 2) {
				intersectionCount += 1;
			} else if ((rect.max_x - rect.min_x) == 2 || (rect.max_y - rect.min_y) == 2) {
				wideRoadCount += 1;
			} else if ((rect.max_x - rect.min_x) == 1 || (rect.max_y - rect.min_y) == 1) {
				slimRoadCount += 1;
			} else if ((rect.max_x - rect.min_x) == 0 || (rect.max_y - rect.min_y) == 0) {
				pathCount += 1;
			} else {
				System.out.println("WHAT?");
				System.out.println(rect);
			}
		}
		//System.out.println("Intersections: " + intersectionCount);
		//System.out.println("Wide roads: " + wideRoadCount);
		//System.out.println("Slim roads: " + slimRoadCount);
		//System.out.println("Paths: " + pathCount);

		List<Coord> coords = getTurningCoords(newRectMap);
		Map<Coord, List<Coord>> paths = getTurningPaths(newRectMap, coords);

		//drawCoordList(coords);

		//System.out.println("Total number of corners: " + coords.size());
		//System.out.println("Total number of directions: " + paths.size());

		//System.out.println("Coords: " + coords);

		return new RoadNetwork(coords, paths, fullMapSizeX, fullMapSizeZ);

	}

	private String getContentFromURL(URL url) throws Exception {
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		con.setRequestMethod("GET");
		BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
		String inputLine;
		StringBuffer content = new StringBuffer();
		while ((inputLine = in.readLine()) != null) {
			content.append(inputLine);
		}
		in.close();
		con.disconnect();
		String response = content.toString();
		return response;
	}

	private int getIndexOfCoord(List<Coord> coords, int x, int y, int xOffset, int yOffset, int wideOffsetX, int wideOffsetY, int slimOffsetX, int slimOffsetY) {
		for (int i = -1; i <= 1; i++) {
			for (int j = -1; j <= 1; j++) {
				int insideWide = coords.indexOf(new Coord(
							x*CHUNK_SIZE + i*fullMapSizeX + xOffset + wideOffsetX,
							y*CHUNK_SIZE + j*fullMapSizeZ + yOffset + wideOffsetY));
				int insideSlim = coords.indexOf(new Coord(
							x*CHUNK_SIZE + i*fullMapSizeX + xOffset + slimOffsetX,
							y*CHUNK_SIZE + j*fullMapSizeZ + yOffset + slimOffsetY));
				if (insideWide != -1) {
					return insideWide;
				}
				if (insideSlim != -1) {
					return insideSlim;
				}
			}
		}
		return -1;
	}

	private Map<Coord, List<Coord>> getTurningPaths(List<Rect> rectMap, List<Coord> coords) {
		Map<Coord, List<Coord>> paths = new HashMap<>();
		for (Rect rect : rectMap) {
			// Get coords for each end
			//int n_min = coords.indexOf(new Coord(
						//rect.min_x*CHUNK_SIZE + WIDE_OFFSET_MIN,
						//rect.max_y*CHUNK_SIZE + CHUNK_SIZE-1
					//)) != -1 ? coords.indexOf(new Coord(
						//rect.min_x*CHUNK_SIZE + WIDE_OFFSET_MIN,
						//rect.max_y*CHUNK_SIZE + CHUNK_SIZE-1
					//)) : coords.indexOf(new Coord(
						//rect.min_x*CHUNK_SIZE + SLIM_OFFSET_MIN,
						//rect.max_y*CHUNK_SIZE + CHUNK_SIZE-1));
			//int n_max = coords.indexOf(new Coord(
						//rect.max_x*CHUNK_SIZE + WIDE_OFFSET_MAX,
						//rect.max_y*CHUNK_SIZE + CHUNK_SIZE-1 + 1
					//)) != -1 ? coords.indexOf(new Coord(
						//rect.max_x*CHUNK_SIZE + WIDE_OFFSET_MAX,
						//rect.max_y*CHUNK_SIZE + CHUNK_SIZE-1 + 1
					//)) : coords.indexOf(new Coord(
						//rect.max_x*CHUNK_SIZE + SLIM_OFFSET_MAX,
						//rect.max_y*CHUNK_SIZE + CHUNK_SIZE-1 + 1));
			//int s_min = coords.indexOf(new Coord(
						//rect.min_x*CHUNK_SIZE + WIDE_OFFSET_MIN,
						//rect.min_y*CHUNK_SIZE - 1
					//)) != -1 ? coords.indexOf(new Coord(
						//rect.min_x*CHUNK_SIZE + WIDE_OFFSET_MIN,
						//rect.min_y*CHUNK_SIZE - 1
					//)) : coords.indexOf(new Coord(
						//rect.min_x*CHUNK_SIZE + SLIM_OFFSET_MIN,
						//rect.min_y*CHUNK_SIZE - 1));
			//int s_max = coords.indexOf(new Coord(
						//rect.max_x*CHUNK_SIZE + WIDE_OFFSET_MAX,
						//rect.min_y*CHUNK_SIZE
					//)) != -1 ? coords.indexOf(new Coord(
						//rect.max_x*CHUNK_SIZE + WIDE_OFFSET_MAX,
						//rect.min_y*CHUNK_SIZE
					//)) : coords.indexOf(new Coord(
						//rect.max_x*CHUNK_SIZE + SLIM_OFFSET_MAX,
						//rect.min_y*CHUNK_SIZE));
			//int e_min = coords.indexOf(new Coord(
						//rect.max_x*CHUNK_SIZE + CHUNK_SIZE-1 + 1,
						//rect.min_y*CHUNK_SIZE + WIDE_OFFSET_MIN
					//)) != -1 ? coords.indexOf(new Coord(
						//rect.max_x*CHUNK_SIZE + CHUNK_SIZE-1 + 1,
						//rect.min_y*CHUNK_SIZE + WIDE_OFFSET_MIN
					//)) : coords.indexOf(new Coord(
						//rect.max_x*CHUNK_SIZE + CHUNK_SIZE-1 + 1,
						//rect.min_y*CHUNK_SIZE + SLIM_OFFSET_MIN));
			//int e_max = coords.indexOf(new Coord(
						//rect.max_x*CHUNK_SIZE + CHUNK_SIZE-1,
						//rect.max_y*CHUNK_SIZE + WIDE_OFFSET_MAX
					//)) != -1 ? coords.indexOf(new Coord(
						//rect.max_x*CHUNK_SIZE + CHUNK_SIZE-1,
						//rect.max_y*CHUNK_SIZE + WIDE_OFFSET_MAX
					//)) : coords.indexOf(new Coord(
						//rect.max_x*CHUNK_SIZE + CHUNK_SIZE-1,
						//rect.max_y*CHUNK_SIZE + SLIM_OFFSET_MAX));
			//int w_min = coords.indexOf(new Coord(
						//rect.min_x*CHUNK_SIZE,
						//rect.min_y*CHUNK_SIZE + WIDE_OFFSET_MIN
					//)) != -1 ? coords.indexOf(new Coord(
						//rect.min_x*CHUNK_SIZE,
						//rect.min_y*CHUNK_SIZE + WIDE_OFFSET_MIN
					//)) : coords.indexOf(new Coord(
						//rect.min_x*CHUNK_SIZE,
						//rect.min_y*CHUNK_SIZE + SLIM_OFFSET_MIN));
			//int w_max = coords.indexOf(new Coord(
						//rect.min_x*CHUNK_SIZE - 1,
						//rect.max_y*CHUNK_SIZE + WIDE_OFFSET_MAX
					//)) != -1 ? coords.indexOf(new Coord(
						//rect.min_x*CHUNK_SIZE - 1,
						//rect.max_y*CHUNK_SIZE + WIDE_OFFSET_MAX
					//)) : coords.indexOf(new Coord(
						//rect.min_x*CHUNK_SIZE - 1,
						//rect.max_y*CHUNK_SIZE + SLIM_OFFSET_MAX));
			int n_min = getIndexOfCoord(coords, rect.min_x, rect.max_y, 0, CHUNK_SIZE-1, WIDE_OFFSET_MIN, 0, SLIM_OFFSET_MIN, 0);
			int n_max = getIndexOfCoord(coords, rect.max_x, rect.max_y, 0, CHUNK_SIZE, WIDE_OFFSET_MAX, 0, SLIM_OFFSET_MAX, 0);
			int s_min = getIndexOfCoord(coords, rect.min_x, rect.min_y, 0, -1, WIDE_OFFSET_MIN, 0, SLIM_OFFSET_MIN, 0);
			int s_max = getIndexOfCoord(coords, rect.max_x, rect.min_y, 0, 0, WIDE_OFFSET_MAX, 0, SLIM_OFFSET_MAX, 0);
			int e_min = getIndexOfCoord(coords, rect.max_x, rect.min_y, CHUNK_SIZE, 0, 0, WIDE_OFFSET_MIN, 0, SLIM_OFFSET_MIN);
			int e_max = getIndexOfCoord(coords, rect.max_x, rect.max_y, CHUNK_SIZE-1, 0, 0, WIDE_OFFSET_MAX, 0, SLIM_OFFSET_MAX);
			int w_min = getIndexOfCoord(coords, rect.min_x, rect.min_y, 0, 0, 0, WIDE_OFFSET_MIN, 0, SLIM_OFFSET_MIN);
			int w_max = getIndexOfCoord(coords, rect.min_x, rect.max_y, -1, 0, 0, WIDE_OFFSET_MAX, 0, SLIM_OFFSET_MAX);
			if ((rect.max_x - rect.min_x) <= 2 && (rect.max_y - rect.min_y) <= 2) {
				// Intersection
				// Link coords
				addPath(coords.get(n_min), coords.get(w_max), paths);
				addPath(coords.get(n_min), coords.get(e_min), paths);
				addPath(coords.get(n_min), coords.get(s_min), paths);

				addPath(coords.get(w_min), coords.get(n_max), paths);
				addPath(coords.get(w_min), coords.get(e_min), paths);
				addPath(coords.get(w_min), coords.get(s_min), paths);

				addPath(coords.get(s_max), coords.get(n_max), paths);
				addPath(coords.get(s_max), coords.get(w_max), paths);
				addPath(coords.get(s_max), coords.get(e_min), paths);

				addPath(coords.get(e_max), coords.get(n_max), paths);
				addPath(coords.get(e_max), coords.get(w_max), paths);
				addPath(coords.get(e_max), coords.get(s_min), paths);
			} else if ((rect.max_x - rect.min_x) <= 2) {
				// NS-road
				// Link coords
				addPath(coords.get(n_min)
						, coords.get(s_min), paths);
				addPath(coords.get(s_max), coords.get(n_max), paths);
			} else {
				// EW-road
				// Link coords
				addPath(coords.get(w_min), coords.get(e_min), paths);
				addPath(coords.get(e_max), coords.get(w_max), paths);
			}

		}
		return paths;
	}

	private void addPath(Coord c1, Coord c2, Map<Coord, List<Coord>> paths) {
		if (paths.get(c1) == null) {
			List<Coord> list = new ArrayList<>();
			list.add(c2);
			paths.put(c1, list);
		} else {
			paths.get(c1).add(c2);
		}
	}


	private List<Coord> getTurningCoords(List<Rect> rectMap) {
		Set<Coord> coords = new HashSet<>();
		for (Rect rect : rectMap) {
			if ((rect.max_x - rect.min_x) == 2) {
				// Wide NS-road
				coords.add(new Coord((rect.min_x*CHUNK_SIZE + WIDE_OFFSET_MIN)%fullMapSizeX, (rect.min_y*CHUNK_SIZE - 1)%fullMapSizeZ)); // s_min
				coords.add(new Coord((rect.max_x*CHUNK_SIZE + WIDE_OFFSET_MAX)%fullMapSizeX, (rect.min_y*CHUNK_SIZE)%fullMapSizeZ)); // s_max
				coords.add(new Coord((rect.min_x*CHUNK_SIZE + WIDE_OFFSET_MIN)%fullMapSizeX, (rect.max_y*CHUNK_SIZE + CHUNK_SIZE-1)%fullMapSizeZ)); // n_min
				coords.add(new Coord((rect.max_x*CHUNK_SIZE + WIDE_OFFSET_MAX)%fullMapSizeX, (rect.max_y*CHUNK_SIZE + CHUNK_SIZE-1 + 1)%fullMapSizeZ)); // n_max
			}
			if ((rect.max_y - rect.min_y) == 2) {
				// Wide EW-road
				coords.add(new Coord((rect.max_x*CHUNK_SIZE + CHUNK_SIZE-1 + 1)%fullMapSizeX, (rect.min_y*CHUNK_SIZE + WIDE_OFFSET_MIN)%fullMapSizeZ)); // e_min
				coords.add(new Coord((rect.max_x*CHUNK_SIZE + CHUNK_SIZE-1)%fullMapSizeX, (rect.max_y*CHUNK_SIZE + WIDE_OFFSET_MAX)%fullMapSizeZ)); // e_max
				coords.add(new Coord((rect.min_x*CHUNK_SIZE)%fullMapSizeX, (rect.min_y*CHUNK_SIZE + WIDE_OFFSET_MIN)%fullMapSizeZ)); // w_min
				coords.add(new Coord((rect.min_x*CHUNK_SIZE - 1)%fullMapSizeX, (rect.max_y*CHUNK_SIZE + WIDE_OFFSET_MAX)%fullMapSizeZ)); // w_max
			}
			if ((rect.max_x - rect.min_x) == 1) {
				// Slim NS-road
				coords.add(new Coord((rect.min_x*CHUNK_SIZE + SLIM_OFFSET_MIN)%fullMapSizeX, (rect.min_y*CHUNK_SIZE - 1)%fullMapSizeZ)); // s_min
				coords.add(new Coord((rect.max_x*CHUNK_SIZE + SLIM_OFFSET_MAX)%fullMapSizeX, (rect.min_y*CHUNK_SIZE)%fullMapSizeZ)); // s_max
				coords.add(new Coord((rect.min_x*CHUNK_SIZE + SLIM_OFFSET_MIN)%fullMapSizeX, (rect.max_y*CHUNK_SIZE + CHUNK_SIZE-1)%fullMapSizeZ)); // n_min
				coords.add(new Coord((rect.max_x*CHUNK_SIZE + SLIM_OFFSET_MAX)%fullMapSizeX, (rect.max_y*CHUNK_SIZE + CHUNK_SIZE-1 + 1)%fullMapSizeZ)); // n_max
			}
			if ((rect.max_y - rect.min_y) == 1) {
				// Slim EW-road
				coords.add(new Coord((rect.max_x*CHUNK_SIZE + CHUNK_SIZE-1 + 1)%fullMapSizeX, (rect.min_y*CHUNK_SIZE + SLIM_OFFSET_MIN)%fullMapSizeZ)); // e_min
				coords.add(new Coord((rect.max_x*CHUNK_SIZE + CHUNK_SIZE-1)%fullMapSizeX, (rect.max_y*CHUNK_SIZE + SLIM_OFFSET_MAX)%fullMapSizeZ)); // e_max
				coords.add(new Coord((rect.min_x*CHUNK_SIZE)%fullMapSizeX, (rect.min_y*CHUNK_SIZE + SLIM_OFFSET_MIN)%fullMapSizeZ)); // w_min
				coords.add(new Coord((rect.min_x*CHUNK_SIZE - 1)%fullMapSizeX, (rect.max_y*CHUNK_SIZE + SLIM_OFFSET_MAX)%fullMapSizeZ)); // w_max
			}
			if ((rect.max_x - rect.min_x) == 0) {
				// NS-path
				coords.add(new Coord((rect.min_x*CHUNK_SIZE + SLIM_OFFSET_MIN)%fullMapSizeX, (rect.min_y*CHUNK_SIZE - 1)%fullMapSizeZ)); // s_min
				coords.add(new Coord((rect.max_x*CHUNK_SIZE + SLIM_OFFSET_MAX)%fullMapSizeX, (rect.min_y*CHUNK_SIZE)%fullMapSizeZ)); // s_max
				coords.add(new Coord((rect.min_x*CHUNK_SIZE + SLIM_OFFSET_MIN)%fullMapSizeX, (rect.max_y*CHUNK_SIZE + CHUNK_SIZE-1)%fullMapSizeZ)); // n_min
				coords.add(new Coord((rect.max_x*CHUNK_SIZE + SLIM_OFFSET_MAX)%fullMapSizeX, (rect.max_y*CHUNK_SIZE + CHUNK_SIZE-1 + 1)%fullMapSizeZ)); // n_max
			}
			if ((rect.max_y - rect.min_y) == 0) {
				// EW-path
				coords.add(new Coord((rect.max_x*CHUNK_SIZE + CHUNK_SIZE-1 + 1)%fullMapSizeX, (rect.min_y*CHUNK_SIZE + SLIM_OFFSET_MIN)%fullMapSizeZ)); // e_min
				coords.add(new Coord((rect.max_x*CHUNK_SIZE + CHUNK_SIZE-1)%fullMapSizeX, (rect.max_y*CHUNK_SIZE + SLIM_OFFSET_MAX)%fullMapSizeZ)); // e_max
				coords.add(new Coord((rect.min_x*CHUNK_SIZE)%fullMapSizeX, (rect.min_y*CHUNK_SIZE + SLIM_OFFSET_MIN)%fullMapSizeZ)); // w_min
				coords.add(new Coord((rect.min_x*CHUNK_SIZE - 1)%fullMapSizeX, (rect.max_y*CHUNK_SIZE + SLIM_OFFSET_MAX)%fullMapSizeZ)); // w_max
			}
		}
		return new ArrayList<>(coords);
	}

	private List<Rect> longSplitRects(List<Rect> oldRectMap) {
		List<Rect> newRectMap = new ArrayList<>(oldRectMap);
		// For every rect, check if another rect is adjecent to the north or south
		// If there is, split both rects along the others x-coords and continue the while-loop
		// For every rect, check if another rect is adjecent to the east or west
		// If there is, split both rects along the others y-coords and continue the while-loop
		for (Rect rect1 : oldRectMap) {
			if (rect1.max_x - rect1.min_x >= 9) {
				newRectMap.remove(rect1);
				newRectMap.add(new Rect(rect1.min_x, rect1.min_y, rect1.min_x + 6, rect1.max_y, this));
				newRectMap.add(new Rect(rect1.min_x + 7, rect1.min_y, rect1.max_x, rect1.max_y, this));
				return newRectMap;
			}
			if (rect1.max_y - rect1.min_y >= 9) {
				newRectMap.remove(rect1);
				newRectMap.add(new Rect(rect1.min_x, rect1.min_y, rect1.max_x, rect1.min_y + 6, this));
				newRectMap.add(new Rect(rect1.min_x, rect1.min_y + 7, rect1.max_x, rect1.max_y, this));
				return newRectMap;
			}
		}
		return newRectMap;
	}

	private List<Rect> mergeRects(List<Rect> oldRectMap) {
		List<Rect> newRectMap = new ArrayList<>(oldRectMap);
		for (Rect rect1 : oldRectMap) {
			for (Rect rect2 : oldRectMap) {
				if (rect1.adjecentToNorth(rect2) || rect1.adjecentToSouth(rect2)) {
					if (rect1.max_x == rect2.max_x && rect1.min_x == rect2.min_x) {
							//rect1.max_x - rect1.min_x >= Math.max(rect1.max_y, rect2.max_y) - Math.min(rect1.min_y, rect2.min_y)) {
						for (Rect rect3 : oldRectMap) {
							if (rect1.adjecentToEast(rect3)) {
								for (Rect rect4 : oldRectMap) {
									if (rect2.adjecentToEast(rect4)) {
										if ((Math.min(rect1.min_y, rect2.min_y) == Math.min(rect3.min_y, rect4.min_y)) && // If the minimum of the two rects to consider is the minimum of the one/two rects to compare to
											(Math.max(rect1.max_y, rect2.max_y) == Math.max(rect3.max_y, rect4.max_y)) && // If the maximum of the two rects to consider is the maximum of the one/two rects to compare to
											(Math.max(rect3.min_y, rect4.min_y) <= Math.min(rect3.max_y, rect4.max_y) + 1) && // If the one/two rects to compare to is continuous
											(Math.max(Math.max(rect1.max_x, rect2.max_x), Math.max(rect3.max_x, rect4.max_x)) - Math.min(Math.min(rect1.min_x, rect2.min_x), Math.min(rect3.min_x, rect4.min_x)) >= Math.max(Math.max(rect1.max_y, rect2.max_y), Math.max(rect3.max_y, rect4.max_y)) - Math.min(Math.min(rect1.min_y, rect2.min_y), Math.min(rect3.min_y, rect4.min_y)))) {
											newRectMap.remove(rect1);
											newRectMap.remove(rect2);
											newRectMap.add(new Rect(rect1.min_x, Math.min(rect1.min_y, rect2.min_y), rect1.max_x, Math.max(rect1.max_y, rect2.max_y), this));
											return newRectMap;
										}
									}
								}
							} else if (rect1.adjecentToWest(rect3)) {
								for (Rect rect4 : oldRectMap) {
									if (rect2.adjecentToWest(rect4)) {
										if ((Math.min(rect1.min_y, rect2.min_y) == Math.min(rect3.min_y, rect4.min_y)) && // If the minimum of the two rects to consider is the minimum of the one/two rects to compare to
											(Math.max(rect1.max_y, rect2.max_y) == Math.max(rect3.max_y, rect4.max_y)) && // If the maximum of the two rects to consider is the maximum of the one/two rects to compare to
											(Math.max(rect3.min_y, rect4.min_y) <= Math.min(rect3.max_y, rect4.max_y) + 1) && // If the one/two rects to compare to is continuous
											(Math.max(Math.max(rect1.max_x, rect2.max_x), Math.max(rect3.max_x, rect4.max_x)) - Math.min(Math.min(rect1.min_x, rect2.min_x), Math.min(rect3.min_x, rect4.min_x)) >= Math.max(Math.max(rect1.max_y, rect2.max_y), Math.max(rect3.max_y, rect4.max_y)) - Math.min(Math.min(rect1.min_y, rect2.min_y), Math.min(rect3.min_y, rect4.min_y)))) {
											newRectMap.remove(rect1);
											newRectMap.remove(rect2);
											newRectMap.add(new Rect(rect1.min_x, Math.min(rect1.min_y, rect2.min_y), rect1.max_x, Math.max(rect1.max_y, rect2.max_y), this));
											return newRectMap;
										}
									}
								}
							}
						}
					}
				}
				if (rect1.adjecentToEast(rect2) || rect1.adjecentToWest(rect2)) {
					if (rect1.max_y == rect2.max_y && rect1.min_y == rect2.min_y) {
							//rect1.max_y - rect1.min_y >= Math.max(rect1.max_x, rect2.max_x) - Math.min(rect1.min_x, rect2.min_x)) {
						for (Rect rect3 : oldRectMap) {
							if (rect1.adjecentToSouth(rect3)) {
								for (Rect rect4 : oldRectMap) {
									if (rect2.adjecentToSouth(rect4)) {
										if ((Math.min(rect1.min_x, rect2.min_x) == Math.min(rect3.min_x, rect4.min_x)) && // If the minimum of the two rects to consider is the minimum of the one/two rects to compare to
											(Math.max(rect1.max_x, rect2.max_x) == Math.max(rect3.max_x, rect4.max_x)) && // If the maximum of the two rects to consider is the maximum of the one/two rects to compare to
											(Math.max(rect3.min_x, rect4.min_x) <= Math.min(rect3.max_x, rect4.max_x) + 1) && // If the one/two rects to compare to is continuous
											(Math.max(Math.max(rect1.max_x, rect2.max_x), Math.max(rect3.max_x, rect4.max_x)) - Math.min(Math.min(rect1.min_x, rect2.min_x), Math.min(rect3.min_x, rect4.min_x)) <= Math.max(Math.max(rect1.max_y, rect2.max_y), Math.max(rect3.max_y, rect4.max_y)) - Math.min(Math.min(rect1.min_y, rect2.min_y), Math.min(rect3.min_y, rect4.min_y)))) {
											newRectMap.remove(rect1);
											newRectMap.remove(rect2);
											newRectMap.add(new Rect(Math.min(rect1.min_x, rect2.min_x), rect1.min_y, Math.max(rect1.max_x, rect2.max_x), rect1.max_y, this));
											return newRectMap;
										}
									}
								}
							} else if (rect1.adjecentToNorth(rect3)) {
								for (Rect rect4 : oldRectMap) {
									if (rect2.adjecentToNorth(rect4)) {
										if ((Math.min(rect1.min_x, rect2.min_x) == Math.min(rect3.min_x, rect4.min_x)) && // If the minimum of the two rects to consider is the minimum of the one/two rects to compare to
											(Math.max(rect1.max_x, rect2.max_x) == Math.max(rect3.max_x, rect4.max_x)) && // If the maximum of the two rects to consider is the maximum of the one/two rects to compare to
											(Math.max(rect3.min_x, rect4.min_x) <= Math.min(rect3.max_x, rect4.max_x) + 1) && // If the one/two rects to compare to is continuous
											(Math.max(Math.max(rect1.max_x, rect2.max_x), Math.max(rect3.max_x, rect4.max_x)) - Math.min(Math.min(rect1.min_x, rect2.min_x), Math.min(rect3.min_x, rect4.min_x)) <= Math.max(Math.max(rect1.max_y, rect2.max_y), Math.max(rect3.max_y, rect4.max_y)) - Math.min(Math.min(rect1.min_y, rect2.min_y), Math.min(rect3.min_y, rect4.min_y)))) {
											newRectMap.remove(rect1);
											newRectMap.remove(rect2);
											newRectMap.add(new Rect(Math.min(rect1.min_x, rect2.min_x), rect1.min_y, Math.max(rect1.max_x, rect2.max_x), rect1.max_y, this));
											return newRectMap;
										}
									}
								}
							}
						}
					}
				}
			}
		}
		return newRectMap;
	}

	private List<Rect> splitRects(List<Rect> oldRectMap) {
		List<Rect> newRectMap = new ArrayList<>(oldRectMap);
		// For every rect, check if another rect is adjecent to the north or south
		// If there is, split both rects along the others x-coords and continue the while-loop
		// For every rect, check if another rect is adjecent to the east or west
		// If there is, split both rects along the others y-coords and continue the while-loop
		for (Rect rect1 : oldRectMap) {
			for (Rect rect2 : oldRectMap) {
				if (rect1.adjecentToNorth(rect2) || rect1.adjecentToSouth(rect2)) {
					if (rect1.min_x < rect2.min_x && rect1.max_x > rect2.min_x) {
						if (rect1.max_x > rect2.max_x) {
							//System.out.println("TEST1");

							// 1x 2x 2x 1x
							newRectMap.remove(rect1);
							newRectMap.add(new Rect(rect1.min_x, rect1.min_y, rect2.min_x-1, rect1.max_y, this));
							newRectMap.add(new Rect(rect2.min_x, rect1.min_y, rect2.max_x, rect1.max_y, this));
							newRectMap.add(new Rect(rect2.max_x+1, rect1.min_y, rect1.max_x, rect1.max_y, this));
							return newRectMap;
						} else if (rect1.max_x < rect2.max_x) {
							//System.out.println("TEST2");
							// 1x 2x 1x 2x
							newRectMap.remove(rect1);
							newRectMap.add(new Rect(rect1.min_x, rect1.min_y, rect2.min_x-1, rect1.max_y, this));
							newRectMap.add(new Rect(rect2.min_x, rect1.min_y, rect1.max_x, rect1.max_y, this));

							newRectMap.remove(rect2);
							newRectMap.add(new Rect(rect2.min_x, rect2.min_y, rect1.max_x, rect2.max_y, this));
							newRectMap.add(new Rect(rect1.max_x+1, rect2.min_y, rect2.max_x, rect2.max_y, this));
							return newRectMap;
						} else if (rect1.max_x == rect2.max_x) {
							//System.out.println("TEST3");
							// 1x 2x 12x

							newRectMap.remove(rect1);
							newRectMap.add(new Rect(rect1.min_x, rect1.min_y, rect2.min_x-1, rect1.max_y, this));
							newRectMap.add(new Rect(rect2.min_x, rect1.min_y, rect1.max_x, rect1.max_y, this));
							return newRectMap;
						}
					} else if (rect1.min_x > rect2.min_x && rect2.max_x > rect1.min_x) {
						if (rect1.max_x < rect2.max_x) {
							//System.out.println("TEST4");
							// 2x 1x 1x 2x
							newRectMap.remove(rect2);
							newRectMap.add(new Rect(rect2.min_x, rect2.min_y, rect1.min_x-1, rect2.max_y, this));
							newRectMap.add(new Rect(rect1.min_x, rect2.min_y, rect1.max_x, rect2.max_y, this));
							newRectMap.add(new Rect(rect1.max_x+1, rect2.min_y, rect2.max_x, rect2.max_y, this));
							return newRectMap;
						} else if (rect1.max_x > rect2.max_x) {
							//System.out.println("TEST5");
							// 2x 1x 2x 1x
							newRectMap.remove(rect2);
							newRectMap.add(new Rect(rect2.min_x, rect2.min_y, rect1.min_x-1, rect2.max_y, this));
							newRectMap.add(new Rect(rect1.min_x, rect2.min_y, rect2.max_x, rect2.max_y, this));

							newRectMap.remove(rect1);
							newRectMap.add(new Rect(rect1.min_x, rect1.min_y, rect2.max_x, rect1.max_y, this));
							newRectMap.add(new Rect(rect2.max_x+1, rect1.min_y, rect1.max_x, rect1.max_y, this));
							return newRectMap;
						} else if (rect1.max_x == rect2.max_x) {
							//System.out.println("TEST6");
							// 2x 1x 21x
							newRectMap.remove(rect2);
							newRectMap.add(new Rect(rect2.min_x, rect2.min_y, rect1.min_x-1, rect2.max_y, this));
							newRectMap.add(new Rect(rect1.min_x, rect2.min_y, rect2.max_x, rect2.max_y, this));
							return newRectMap;
						}
					} else if (rect1.min_x == rect2.min_x) {
						if (rect1.max_x < rect2.max_x) {
							//System.out.println("TEST7");

							// 12x 1x 2x
							newRectMap.remove(rect2);
							newRectMap.add(new Rect(rect2.min_x, rect2.min_y, rect1.max_x, rect2.max_y, this));
							newRectMap.add(new Rect(rect1.max_x+1, rect2.min_y, rect2.max_x, rect2.max_y, this));

							return newRectMap;
						} else if (rect1.max_x > rect2.max_x) {
							//System.out.println("TEST8");
							// 12x 2x 1x
							newRectMap.remove(rect1);
							newRectMap.add(new Rect(rect1.min_x, rect1.min_y, rect2.max_x, rect1.max_y, this));
							newRectMap.add(new Rect(rect2.max_x+1, rect1.min_y, rect1.max_x, rect1.max_y, this));
							return newRectMap;
						} else if (rect1.max_x == rect2.max_x) {
							//System.out.println("TEST9");
							// 12x 21x
						}
					}
				}
				if (rect1.adjecentToEast(rect2) || rect1.adjecentToWest(rect2)) {
					if (rect1.min_y < rect2.min_y && rect1.max_y > rect2.min_y) {
						if (rect1.max_y > rect2.max_y) {
							//System.out.println("TEST10");
							// 1y 2y 2y 1y
							newRectMap.remove(rect1);
							newRectMap.add(new Rect(rect1.min_x, rect1.min_y, rect1.max_x, rect2.min_y-1, this));
							newRectMap.add(new Rect(rect1.min_x, rect2.min_y, rect1.max_x, rect2.max_y, this));
							newRectMap.add(new Rect(rect1.min_x, rect2.max_y+1, rect1.max_x, rect1.max_y, this));
							return newRectMap;
						} else if (rect1.max_y < rect2.max_y) {
							//System.out.println("TEST11");
							// 1y 2y 1y 2y
							newRectMap.remove(rect1);
							newRectMap.add(new Rect(rect1.min_x, rect1.min_y, rect1.max_x, rect2.min_y-1, this));
							newRectMap.add(new Rect(rect1.min_x, rect2.min_y, rect1.max_x, rect1.max_y, this));

							newRectMap.remove(rect2);
							newRectMap.add(new Rect(rect2.min_x, rect2.min_y, rect2.max_x, rect1.max_y, this));
							newRectMap.add(new Rect(rect2.min_x, rect1.max_y+1, rect2.max_x, rect2.max_y, this));
							return newRectMap;
						} else if (rect1.max_y == rect2.max_y) {
							//System.out.println("TEST12");
							// 1y 2y 12y
							newRectMap.remove(rect1);
							newRectMap.add(new Rect(rect1.min_x, rect1.min_y, rect1.max_x, rect2.min_y-1, this));
							newRectMap.add(new Rect(rect1.min_x, rect2.min_y, rect1.max_x, rect1.max_y, this));
							return newRectMap;
						}
					} else if (rect1.min_y > rect2.min_y && rect2.max_y > rect1.min_y) {
						if (rect1.max_y < rect2.max_y) {
							//System.out.println("TEST13");
							// 2y 1y 1y 2y
							newRectMap.remove(rect2);
							newRectMap.add(new Rect(rect2.min_x, rect2.min_y, rect2.max_x, rect1.min_y-1, this));
							newRectMap.add(new Rect(rect2.min_x, rect1.min_y, rect2.max_x, rect1.max_y, this));
							newRectMap.add(new Rect(rect2.min_x, rect1.max_y+1, rect2.max_x, rect2.max_y, this));
							return newRectMap;
						} else if (rect1.max_y > rect2.max_y) {
							//System.out.println("TEST14");
							// 2y 1y 2y 1y
							newRectMap.remove(rect2);
							newRectMap.add(new Rect(rect2.min_x, rect2.min_y, rect2.max_x, rect1.min_y-1, this));
							newRectMap.add(new Rect(rect2.min_x, rect1.min_y, rect2.max_x, rect2.max_y, this));

							newRectMap.remove(rect1);
							newRectMap.add(new Rect(rect1.min_x, rect1.min_y, rect1.max_x, rect2.max_y, this));
							newRectMap.add(new Rect(rect1.min_x, rect2.max_y+1, rect1.max_x, rect1.max_y, this));
							return newRectMap;
						} else if (rect1.max_y == rect2.max_y) {
							//System.out.println("TEST15");
							// 2y 1y 21y
							newRectMap.remove(rect2);
							newRectMap.add(new Rect(rect2.min_x, rect2.min_y, rect2.max_x, rect1.min_y-1, this));
							newRectMap.add(new Rect(rect2.min_x, rect1.min_y, rect2.max_x, rect2.max_y, this));
							return newRectMap;
						}
					} else if (rect1.min_y == rect2.min_y) {
						if (rect1.max_y < rect2.max_y) {
							//System.out.println("TEST16");
							// 12y 1y 2y
							newRectMap.remove(rect2);
							newRectMap.add(new Rect(rect2.min_x, rect2.min_y, rect2.max_x, rect1.max_y, this));
							newRectMap.add(new Rect(rect2.min_x, rect1.max_y+1, rect2.max_x, rect2.max_y, this));
							return newRectMap;
						} else if (rect1.max_y > rect2.max_y) {
							//System.out.println("TEST17");
							// 12y 2y 1y
							newRectMap.remove(rect1);
							newRectMap.add(new Rect(rect1.min_x, rect1.min_y, rect1.max_x, rect2.max_y, this));
							newRectMap.add(new Rect(rect1.min_x, rect2.max_y+1, rect1.max_x, rect1.max_y, this));
							return newRectMap;
						} else if (rect1.max_y == rect2.max_y) {
							//System.out.println("TEST18");
							// 12y 21y
						}
					}
				}
			}
		}
		return newRectMap;
	}

	private List<Rect> buildRectMap(boolean[][] binMap) {
		int width = binMap.length;
		int height = binMap[0].length;

		int totalRoadArea = 0;
		for (int i = 0; i < width; i++) {
			for (int j = 0; j < height; j++) {
				totalRoadArea += binMap[i][j] ? 1 : 0;
			}
		}

		//System.out.println("Total road area: " + totalRoadArea);


		ArrayList<Rect> roads = new ArrayList<>();
		int areaCovered = 0;

		while (areaCovered < totalRoadArea) {
			Rect rect = findNextRect(binMap, width, height);
			roads.add(rect);
			markRect(rect, binMap);
			areaCovered += (rect.max_x - rect.min_x + 1) * (rect.max_y - rect.min_y + 1);
		}

		return roads;
	}

	private Rect findNextRect(boolean[][] binMap, int width, int height) {
		Rect result = new Rect(0, 0, width-1, height-1, this);

		boolean foundCorner = false;
		for (int i = 0; i < width; i++) {
			for (int j = 0; j < height; j++) {
				if (binMap[j][i]) {
					result.min_x = i;
					result.min_y = j;
					foundCorner = true;
					break;
				}
			}
			if (foundCorner) {
				break;
			}
		}
		for (int i = result.min_x; i <= result.max_x; i++) {
			if (!binMap[result.min_y][i]) {
				result.max_x = i-1;
				return result;
			}
			for (int j = result.min_y; j <= result.max_y; j++) {
				if (!binMap[j][i]) {
					result.max_y = j-1;
					break;
				}
			}
		}
		return result;
	}

	private void markRect(Rect rect, boolean[][] binMap) {
		for (int i = rect.min_x; i <= rect.max_x; i++) {
			for (int j = rect.min_y; j <= rect.max_y; j++) {
				binMap[j][i] = false;
			}
		}
	}

	public static class Rect {
		public int min_x;
		public int min_y;
		public int max_x;
		public int max_y;

		public Rect(int min_x, int min_y, int max_x, int max_y, MapInterpreter mapInterpreter) {
			this.min_x = min_x % mapInterpreter.mapSizeX;
			this.min_y = min_y % mapInterpreter.mapSizeZ;
			this.max_x = max_x % mapInterpreter.mapSizeX;
			this.max_y = max_y % mapInterpreter.mapSizeZ;
			if (min_y > max_y || min_x > max_x) {
				System.out.println("ILLEGAL RECT");
				System.out.println(this);
			}
		}

		public boolean adjecentToNorth(Rect other) {
			if (this.min_x > other.max_x) {
				return false;
			}
			if (this.max_x < other.min_x) {
				return false;
			}
			return other.min_y == this.max_y + 1;
		}

		public boolean adjecentToSouth(Rect other) {
			if (this.min_x > other.max_x) {
				return false;
			}
			if (this.max_x < other.min_x) {
				return false;
			}
			return other.max_y == this.min_y - 1;
		}

		public boolean adjecentToEast(Rect other) {
			if (this.min_y > other.max_y) {
				return false;
			}
			if (this.max_y < other.min_y) {
				return false;
			}
			return other.min_x == this.max_x + 1;
		}

		public boolean adjecentToWest(Rect other) {
			if (this.min_y > other.max_y) {
				return false;
			}
			if (this.max_y < other.min_y) {
				return false;
			}
			return other.max_x == this.min_x - 1;
		}

		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof Rect)) {
				return false;
			}
			Rect other = (Rect) o;
			if (other.min_x != this.min_x) {
				return false;
			}
			if (other.min_y != this.min_y) {
				return false;
			}
			if (other.max_x != this.max_x) {
				return false;
			}
			if (other.max_y != this.max_y) {
				return false;
			}
			return true;
		}

		public int hashCode() {
			int hash = 7;
			hash = 31 * hash + min_x;
			hash = 31 * hash + min_y;
			hash = 31 * hash + max_x;
			hash = 31 * hash + max_x;
			return hash;
		}


		public String toString() {
			return "(" + min_x + ", " + min_y + ", " + max_x + ", " + max_y + ")";
		}
	}

	private void drawRectMap(List<Rect> rectMap) {
		int factor = 1;
		for (int y = mapSizeZ*factor; y >= 0; y--) {
			for (int x = 0; x <= mapSizeX*factor; x++) {
				boolean foundOne = false;
				int amountFound = 0;
				for (Rect rect : rectMap) {
					if (rect.min_x*factor < x && rect.max_x*factor+factor > x && (rect.min_y*factor == y || rect.max_y*factor+factor == y)) {
						//System.out.print("|");
						foundOne = true;
						amountFound += 1;
					} else if (rect.min_y*factor < y && rect.max_y*factor+factor > y && (rect.min_x*factor == x || rect.max_x*factor+factor == x)) {
						//System.out.print("-");
						foundOne = true;
						amountFound += 1;
					} else if ((rect.min_x*factor == x || rect.max_x*factor+factor == x) && (rect.min_y*factor == y || rect.max_y*factor+factor == y)) {
						//System.out.print("+");
						foundOne = true;
						amountFound += 1;
					}
				}
				if (foundOne == false) {
					System.out.print(" ");
				} else {
					System.out.print(amountFound);
				}

			}
			System.out.println();
		}
	}

	private void drawCoordList(List<Coord> coordList) {
		int factor = 5;
		for (int y = mapSizeZ*factor; y >= 0; y--) {
			for (int x = 0; x <= mapSizeX*factor; x++) {
				boolean foundOne = false;
				for (Coord coord : coordList) {
					if (coord.x == x && coord.y == y) {
						foundOne = true;
						break;
					}
				}
				if (foundOne == false) {
					System.out.print(" ");
				} else {
					System.out.print("*");
				}

			}
			System.out.println();
		}
	}

}
