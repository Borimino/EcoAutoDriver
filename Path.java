public class Path {
	public int start;
	public int end;

	public Path(int start, int end) {
		this.start = start;
		this.end = end;
	}

	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Path)) {
			return false;
		}
		Path other = (Path) o;
		if (other.start != this.start) {
			return false;
		}
		if (other.end != this.end) {
			return false;
		}
		return true;
	}

	public int hashCode() {
		int hash = 7;
		hash = 31 * hash + start;
		hash = 31 * hash + end;
		return hash;
	}


	public String toString() {
		return "(" + start + ", " + end + ")";
	}
}
