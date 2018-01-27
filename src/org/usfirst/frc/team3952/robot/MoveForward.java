package org.usfirst.frc.team3952.robot;

import edu.wpi.first.wpilibj.*;
import edu.wpi.first.wpilibj.drive.*;

public class MoveForward extends Task {
	private MecanumDrive drive;
	private Encoder leftEncoder, rightEncoder;
	private double totalDistance;
	
	public MoveForward(Robot robot, double distance) {
		drive = robot.getDrive();
		leftEncoder = robot.getLeftEncoder();
		rightEncoder = robot.getRightEncoder();
		totalDistance = distance;
	}
	
	@Override
	public boolean run() {
		double currentDistance = (leftEncoder.getDistance() + rightEncoder.getDistance()) / 2;
		if(currentDistance >= totalDistance - 0.1) {
			return true;
		} else {
			drive.driveCartesian(0.3, 0, 0);		// set to a reasonable value
			return false;
		}
	}
	
	@Override
	public void cancel() {
		drive.driveCartesian(0, 0, 0);		// needed?
	}
	
	@Override
	public String toString() {
		double currentDistance = (leftEncoder.getDistance() + rightEncoder.getDistance()) / 2;
		return "Move Forward: " + (int)totalDistance + " feet(" + (int) (currentDistance - totalDistance) + " feet left)";
	}
}