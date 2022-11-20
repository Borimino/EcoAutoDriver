import java.util.*;
import java.util.concurrent.*;
import java.awt.Robot;
import java.awt.AWTException;
import java.awt.event.*;
import javax.swing.*;
import com.sun.jna.*;
import com.sun.jna.platform.*;
import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.*;

public class AutoDriver {

	private static final double GOAL_DISTANCE = 4.5f;
	private static final double PRETTY_STRAIGHT_ANGLE = Math.PI/64f;
	private static final double SHARP_TURN_ANGLE = Math.PI/4f;
	private static final double LOW_SPEED = 3f;
	//private static final double SLEEP_MODIFIER = 16;

	private static int MAX_X;
	private static int MAX_Y;

	private static boolean run = true;
	private static boolean abortDriving = true;

	private static Kernel32 kernel32 = (Kernel32) Native.loadLibrary("kernel32", Kernel32.class);
	private static User32     user32 = (User32)   Native.loadLibrary("user32"  , User32.class);

	private static JDialog debugDialog;
	private static JLabel debugLabel;
	
	public static void main(String[] args) throws Exception {
		MapInterpreter mapInterpreter = new MapInterpreter();
		PathFinder pathFinder = new PathFinder();

		final RoadNetwork roadNetwork = mapInterpreter.getRoadNetwork();
		if (roadNetwork.getCoords().size() == 0) {
			System.out.println("No road network found!");
		}
		MAX_X = roadNetwork.getMapX();
		MAX_Y = roadNetwork.getMapY();
		System.out.println(MAX_X + ", " + MAX_Y);

		JFrame frame = new JFrame();
		frame.setSize(250, 200);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		JPanel panel = new JPanel();
		frame.add(panel);
		panel.setLayout(null);

		JLabel xLabel = new JLabel("x");
		xLabel.setBounds(10, 10, 25, 25);
		panel.add(xLabel);

		JTextField xText = new JTextField(3);
		xText.setBounds(45, 10, 50, 25);
		panel.add(xText);

		JLabel zLabel = new JLabel("z");
		zLabel.setBounds(10, 50, 25, 25);
		panel.add(zLabel);

		JTextField zText = new JTextField(3);
		zText.setBounds(45, 50, 50, 25);
		panel.add(zText);

		JButton startButton = new JButton("Start");
		startButton.setBounds(10, 80, 80, 25);
		panel.add(startButton);

		JLabel status = new JLabel("");
		status.setBounds(10, 110, 80, 25);
		panel.add(status);

		startButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				int x = 0;
				int z = 0;
				try {
					x = Integer.valueOf(xText.getText());
					z = Integer.valueOf(zText.getText());
				} catch (NumberFormatException e) {
					status.setText("Only input numbers please");
					return;
				}
				abortDriving = false;
				try {
					driveTo(new Coord(x, z), roadNetwork, pathFinder);
				} catch (InterruptedException e) {
					// Do nothing
				} catch (AWTException e) {
					throw new RuntimeException(e);
				}

			}
		});

		frame.setVisible(true);
	}

	private static DoubleCoord getPosition() {
		int pid = getProcessId("Eco");
		WinNT.HANDLE readProcess = openProcess(0x0010, pid);
		List<Tlhelp32.MODULEENTRY32W> modules = Kernel32Util.getModules(pid);
		Pointer modulePointer = modules.stream().filter(m -> m.szModule().equals("GameAssembly.dll")).findFirst().map(m -> m.modBaseAddr).get();

		Pointer address = modulePointer;
		Long[] offsets = new Long[] {(long) 0x04388858, (long) 0xB8, (long) 0x20};
		for (Long offset : Arrays.asList(offsets)) {
			int size = 8;
			Pointer.nativeValue(address, Pointer.nativeValue(address) + offset);
			Memory read = readMemory(readProcess, address, size);
			address = read.getPointer(0);
		}

		int size = 4;
		Pointer.nativeValue(address, Pointer.nativeValue(address) + 0x270);
		Memory read = readMemory(readProcess, address, size);
		float x = read.getFloat(0);
		Pointer.nativeValue(address, Pointer.nativeValue(address) + 0x8);
		read = readMemory(readProcess, address, size);
		float z = read.getFloat(0);

		return new DoubleCoord(x, z);

	}

	private static void driveTo(Coord destination, RoadNetwork roadNetwork, PathFinder pathFinder) throws InterruptedException, AWTException {
		createDebugDialog();

		DoubleCoord currentPos = getPosition();
		Coord currentPosition = new Coord((int) currentPos.x, (int) currentPos.y);
		List<PathFinder.Route> routes = pathFinder.findPaths(currentPosition, destination, roadNetwork);

		System.out.println("Paths found: " + routes.size());


		if (routes.size() == 0) {
			debugLabel.setText("No routes found");
			debugDialog.pack();
		}


		while (routes.size() >= 1) {
			System.out.println(routes.get(0));

			List<Coord> currentPath = routes.remove(0).path;

			Coord firstCoord = currentPath.remove(0);

			int result = driveTowards(new DoubleCoord(firstCoord.x, firstCoord.y));
			if (result == -1) { // If we start by driving backwards, try another route
				continue;
			}

			for (Coord dest : currentPath) {
				result = driveTowards(new DoubleCoord(dest.x, dest.y));
				if (result == -1) {
					break;
				}
			}

			return;
		}
	}

	private static int driveTowards(DoubleCoord destination) throws InterruptedException, AWTException {
		//int shouldPrintDebug = 0;
		DoubleCoord previousCoord = null;
		Long lastMillis = null;
		while (true) {
			//if (abortDriving || !run) {
				//stopDriving();
				//return 0;
			//}

			TimeUnit.MILLISECONDS.sleep((long) 16);

			DoubleCoord currentCoord = getPosition();
			long currentMillis = System.currentTimeMillis();

			if (previousCoord == null || lastMillis == null) {
				previousCoord = currentCoord;
				lastMillis = currentMillis;
				continue;
			}

			previousCoord = maybeWrapCoordToGetNearCoord(previousCoord, currentCoord);
			DoubleCoord tmpDestination = maybeWrapCoordToGetNearCoord(destination, currentCoord);

			if (distanceBetween(tmpDestination, currentCoord) < GOAL_DISTANCE) {
				stopDriving();
				//TimeUnit.MILLISECONDS.sleep((long) 128);
				return 0;
			}

			DoubleCoord justTraveled = currentCoord.minus(previousCoord);
			DoubleCoord leftToTravel = tmpDestination.minus(currentCoord);

			double perpendicularDot = justTraveled.y*leftToTravel.x - justTraveled.x*leftToTravel.y;
			double dot = justTraveled.x*leftToTravel.x + justTraveled.y*leftToTravel.y;
			double magnitude = Math.sqrt(justTraveled.x*justTraveled.x + justTraveled.y*justTraveled.y) * Math.sqrt(leftToTravel.x*leftToTravel.x + leftToTravel.y*leftToTravel.y);
			double angle = Math.abs(Math.acos(dot/magnitude));
			double distanceTraveled = Math.sqrt(justTraveled.x*justTraveled.x + justTraveled.y*justTraveled.y);
			double timeTraveledMillis = (currentMillis - lastMillis);
			double timeTraveledSeconds = timeTraveledMillis/1000;
			double speed = distanceTraveled / timeTraveledSeconds;

			if (magnitude >= 32) {
				previousCoord = currentCoord;

				System.out.println("TEST5");
				continue;
			}

			if (distanceTraveled <= 0) {
				continue;
			}

			System.out.println("TEST4");


			printDebugInfo(currentCoord, previousCoord, tmpDestination, perpendicularDot, dot, magnitude, angle, leftToTravel, distanceTraveled, speed, timeTraveledSeconds);

			//if (dot < 0 && magnitude >= 0.5) {
			if (dot <= -0.01) {
				stopDriving();
				return -1;
			} else if (perpendicularDot == 0 || angle < PRETTY_STRAIGHT_ANGLE) {
				driveForwards();
			} else if (angle < SHARP_TURN_ANGLE || speed < LOW_SPEED) {
				if (perpendicularDot > 0) {
					turnRight();
					//TimeUnit.MILLISECONDS.sleep((long) (angle*SLEEP_MODIFIER));
				} else {
					turnLeft();
					//TimeUnit.MILLISECONDS.sleep((long) (angle*SLEEP_MODIFIER));
				}
			} else {
				if (perpendicularDot > 0) {
					turnRightSharp();
					//TimeUnit.MILLISECONDS.sleep((long) (angle*SLEEP_MODIFIER));
				} else {
					turnLeftSharp();
					//TimeUnit.MILLISECONDS.sleep((long) (angle*SLEEP_MODIFIER));
				}
			}

			previousCoord = currentCoord;
			lastMillis = currentMillis;
		}
	}

	private static DoubleCoord maybeWrapCoordToGetNearCoord(DoubleCoord toWrap, DoubleCoord toStay) {
		double minDist = distanceBetween(toWrap, toStay);
		DoubleCoord result = toWrap;

		List<DoubleCoord> toTests = Arrays.asList(
			new DoubleCoord(toWrap.x+MAX_X, toWrap.y),
			new DoubleCoord(toWrap.x+MAX_X, toWrap.y+MAX_Y),
			new DoubleCoord(toWrap.x, toWrap.y+MAX_X),
			new DoubleCoord(toWrap.x-MAX_X, toWrap.y+MAX_Y),
			new DoubleCoord(toWrap.x-MAX_X, toWrap.y),
			new DoubleCoord(toWrap.x-MAX_X, toWrap.y-MAX_Y),
			new DoubleCoord(toWrap.x, toWrap.y-MAX_Y),
			new DoubleCoord(toWrap.x+MAX_X, toWrap.y-MAX_Y)
		);

		for (DoubleCoord toTest : toTests) {
			double tmpDist = distanceBetween(toTest, toStay);
			if (tmpDist < minDist) {
				result = toTest;
				minDist = tmpDist;
			}
		}

		return result;
	}


	private static double distanceBetween(DoubleCoord c1, DoubleCoord c2) {
		return Math.sqrt((c1.x-c2.x)*(c1.x-c2.x) + (c1.y-c2.y)*(c1.y-c2.y));
	}


	private static void driveForwards() throws AWTException{
		Robot r = new Robot();
		r.keyPress(KeyEvent.VK_W);
		r.keyRelease(KeyEvent.VK_A);
		r.keyRelease(KeyEvent.VK_D);
		r.keyRelease(KeyEvent.VK_S);
	}

	private static void turnLeft() throws AWTException{
		Robot r = new Robot();
		r.keyPress(KeyEvent.VK_W);
		r.keyPress(KeyEvent.VK_A);
		r.keyRelease(KeyEvent.VK_D);
		r.keyRelease(KeyEvent.VK_S);
	}

	private static void turnRight() throws AWTException{
		Robot r = new Robot();
		r.keyPress(KeyEvent.VK_W);
		r.keyRelease(KeyEvent.VK_A);
		r.keyPress(KeyEvent.VK_D);
		r.keyRelease(KeyEvent.VK_S);
	}

	private static void turnLeftSharp() throws AWTException{
		Robot r = new Robot();
		r.keyRelease(KeyEvent.VK_W);
		r.keyPress(KeyEvent.VK_A);
		r.keyRelease(KeyEvent.VK_D);
		r.keyRelease(KeyEvent.VK_S);
	}

	private static void turnRightSharp() throws AWTException{
		Robot r = new Robot();
		r.keyRelease(KeyEvent.VK_W);
		r.keyRelease(KeyEvent.VK_A);
		r.keyPress(KeyEvent.VK_D);
		r.keyRelease(KeyEvent.VK_S);
	}

	private static void stopDriving() throws AWTException{
		Robot r = new Robot();
		r.keyRelease(KeyEvent.VK_W);
		r.keyRelease(KeyEvent.VK_A);
		r.keyRelease(KeyEvent.VK_D);
		r.keyRelease(KeyEvent.VK_S);
	}

	private static class DoubleCoord {
		public double x;
		public double y;

		public DoubleCoord(double x, double y) {
			this.x = x;
			this.y = y;
		}

		public DoubleCoord minus(DoubleCoord other) {
			return new DoubleCoord(x-other.x, y-other.y);
		}

		public String toString() {
			return String.format("%.2f", x) + ", " + String.format("%.2f", y);
		}


	}

	private static void printDebugInfo(DoubleCoord currentCoord, DoubleCoord previousCoord, DoubleCoord destination, double perpendicularDot, double dot, double magnitude, double angle, DoubleCoord leftToTravel, double distanceTraveled, double speed, double timeTraveled) {
		debugLabel.setText("<html>Current coord: " + currentCoord + "<br />Previous coord: " + previousCoord + "<br />Destination: " + destination + "<br />Perp dot: " + String.format("%.2f", perpendicularDot) + "<br />Dot: " + String.format("%.2f", dot) + "<br />Magnitude: " + String.format("%.2f", magnitude) + "<br />Angle: " + String.format("%.2f", angle) + "<br />Left to travel: " + leftToTravel + "<br />Distance traveled: " + String.format("%.2f", distanceTraveled) + "<br />Speed: " + String.format("%.2f", speed) + "<br />Time: " + String.format("%.2f", timeTraveled) + "</html>");

		debugDialog.pack();
		debugLabel.paintImmediately(debugLabel.getVisibleRect());

		//debugLabel.revalidate();
		//debugLabel.repaint();

	}

	private static void createDebugDialog() {
		if (debugDialog != null) {
			debugDialog.dispose();
		}
		debugDialog = new JDialog();
		JPanel contentPane = new JPanel();
		debugLabel = new JLabel("Current coord: " + "(000,000,000)" + "\nPrevious coord: " + "(000,000,000)" + "\nDestination: " + "(000,000,000)" + "\nPerp dot: " + "00000000" + "\nDot: " + "00000000" + "\nMagnitude: " + "00000000" + "\nAngle: " + "00000000");
		contentPane.add(debugLabel);
		debugDialog.add(contentPane);

		debugDialog.pack();
		//debugDialog.setFocusableWindowState(false);
		debugDialog.setVisible(true);
		debugDialog.setAlwaysOnTop(true);

	}

    public static int getProcessId(String window)
    {
        IntByReference pid = new IntByReference(0);
        user32.GetWindowThreadProcessId(user32.FindWindowA(null,window), pid);
 
        return pid.getValue();
    }
 
    public static WinNT.HANDLE openProcess(int permissions, int pid)
    {
        WinNT.HANDLE process = kernel32.OpenProcess(permissions,true, pid);
        return process;
    }
 
    public static Memory readMemory(WinNT.HANDLE process, Pointer address, int bytesToRead)
    {
        IntByReference read = new IntByReference(0);
        Memory output = new Memory(bytesToRead);
 
        kernel32.ReadProcessMemory(process, address, output, bytesToRead, read);
        return output;
    }
}
