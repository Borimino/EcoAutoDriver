public class Coord {
	public int x;
	public int y;

	public Coord(int x, int y) {
		this.x = x;
		this.y = y;
	}

	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Coord)) {
			return false;
		}
		Coord other = (Coord) o;
		if (other.x != this.x) {
			return false;
		}
		if (other.y != this.y) {
			return false;
		}
		return true;
	}

	public int hashCode() {
		int hash = 7;
		hash = 31 * hash + x;
		hash = 31 * hash + y;
		return hash;
	}


	public String toString() {
		return "(" + x + ", " + y + ")";
	}
}
