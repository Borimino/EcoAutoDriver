public class Coord {
	public double x;
	public double y;

	public Coord(double x, double y) {
		this.x = x;
		this.y = y;
	}

	public double getX() {
		return x;
	}

	public double getY() {
		return y;
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
		hash = 31 * hash + (int) x*1000;
		hash = 31 * hash + (int) y*1000;
		return hash;
	}

	public String toString() {
		return "(" + x + ", " + y + ")";
	}
}
