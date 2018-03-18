package org.usfirst.frc.team3952.robot;

import edu.wpi.cscore.UsbCamera;
import edu.wpi.first.wpilibj.*;
import java.util.*;
import edu.wpi.first.wpilibj.drive.*;
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;


/**
 * rear left 		3	
 * rear right 		2
 * front right		0
 * front left	 	1
 * ladder 			6
 * climber			4
 * claw				5
 * 
 * Tasks:
 * 	-Limit switches
 * 	-Ladder reset
 *  -MoveForward Straighting test
 *  -
 * 
 */
public class Robot extends IterativeRobot {
	//=== Drive ===\\	
	private Controller controller;

	private Talon frontLeft, frontRight, rearLeft, rearRight;
	private Encoder rightEncoder, leftEncoder;
	private ADXRS450_Gyro gyro;
	private MecanumDrive drive;
	
	//=== Ladder, Claw, & Climber ===\\
	
	private Talon ladderT, coiler, claw;
	private Encoder ladderEncoder;
	private DigitalInput topLimit, armBottomLimit, clawOpeningLimit, clawClosingLimit;
	private Ladder ladder;
	private Climber climber;

	//=== Task System ===\\	
	private Task currentTask;
	private Queue<Task> autonomousQueue;
	private SendableChooser<String> autonomousChooser;

	private boolean clawWillOpen;
	public static long startMillis;
//	public static boolean shouldStop = false;			// not used
		
	//=== Camera ===\\
	private UsbCamera camera;
	
	@Override
	public void robotInit() {
		
		System.out.println("Entering Init");
		//=== Drive Initialization ===\\
		controller = new SideWinderController(); //or new CircularDeadzoneController or new BadController()
		
		frontLeft = new HandicappedTalon(1, 0);
		frontRight = new HandicappedTalon(0,0);
		rearLeft = new HandicappedTalon(3,0);
		rearRight = new HandicappedTalon(2,0);			
		
		// Initialize Encoders
		rightEncoder = new Encoder(2, 3, false, Encoder.EncodingType.k1X); // We can also try k4 for more accuracy.
		rightEncoder.setDistancePerPulse(0.0078);		
		leftEncoder = new Encoder(1, 0, false, Encoder.EncodingType.k1X);
		leftEncoder.setDistancePerPulse(0.00677);
		
		gyro = new ADXRS450_Gyro();
		
		// Initialize Drive Train
		
		drive = new MecanumDrive(frontLeft,
								 rearLeft, 
								 frontRight, 
								 rearRight);
		
		//=== Ladder, Claw, & Winch Initialization ===\\
		
		ladderT = new Talon(6);		
		coiler = new Talon(4);    
		claw = new Talon(5);
		
		
		ladderEncoder = new Encoder(4, 5, false, Encoder.EncodingType.k2X);
		ladderEncoder.setDistancePerPulse(1);	// We are not going to calibrate
		
		topLimit = new DigitalInput(6);
		armBottomLimit = new DigitalInput(7);
		clawOpeningLimit = new DigitalInput(8);
		clawClosingLimit = new DigitalInput(9); 
		
		ladder = new Ladder(ladderT, coiler, claw, ladderEncoder, topLimit, armBottomLimit, clawOpeningLimit, clawClosingLimit);
		
		climber = new Climber(coiler);
		//=== Task System Initialization===\\
		
		autonomousQueue = new LinkedList<>();
		currentTask = new TeleopTask(this);
//		gyro.calibrate(); // Not necessary
		
		// SmartDashboard selecting autonomous
		autonomousChooser = new SendableChooser<>();
		autonomousChooser.addObject("Starting Left", "L");
		autonomousChooser.addObject("Starting Middle", "M");
		autonomousChooser.addDefault("Starting Right", "R");
		SmartDashboard.putData("Autonomous Initial Position", autonomousChooser);
		
//		startMillis = System.currentTimeMillis();
//		shouldStop = false;
		
		camera = CameraServer.getInstance().startAutomaticCapture();
		camera.setResolution(640, 480); //(160, 120)
		
		stopMotors();
	}
	//=== Disabled ===\\
	
	@Override
	public void disabledInit() {}

	//=== Teleop ===\\
	@Override
	public void teleopInit() {
		stopMotors();
		currentTask = new TeleopTask(this);
		autonomousQueue = new LinkedList<>();
	}
	
	
	/** called every ~20 ms
	 *  Notes: It seems like Task is overkill in Teleop Periodic...
	 *  In 2018 we just ended up making versions of system controlling methods for telop and then creating tasks which will do that 
	 *  autonomously. 
	 *  The only time that the task system will be useful is if we have pre programmed commands the drivers
	 *  can activate. For that reason, we should def keep it and someday refactor ladder and coiler into teleop
	 */
	@Override
	public void teleopPeriodic() {		
		//---------- Task running----------------------------------------//
		if(currentTask.run()){
			//don't run cancel bc the last iteration of every tasks should clean up everything
			currentTask = new TeleopTask(this);
		}
		
		// ---------------SmartDashboard----------------------------------//
		displayOnSmartDashboard();
	}
	
	
	//==== Autonomous ===//
	
	/**
	 * This will run every time you press enable in auto.
	 * In game message is available (confirmed)
	 * 
	 */
	@Override
	public void autonomousInit(){
		stopMotors(); //make sure everything is stopped.
	
		//------------------get information----------------------------------//
		String stuff = DriverStation.getInstance().getGameSpecificMessage(); // e.g. LRL
		String switchPos = stuff.substring(0, 1);
		String scalePos = stuff.substring(1, 2);
		String ourPosition = "R"; // L, R, M //
		SmartDashboard.putString("In Game Message", stuff);
		
		
		//--------------------------actual decision making----------------------//
		autonomousQueue = new LinkedList<>(); //clears everything..
		autonomousQueue.add(new DropClawTask(this)); //always drop claw
		
		if(ourPosition.equals("L")){
			//not using fancy queued task method.
			autonomousQueue.add(new MultiTask(new MoveForwardTask(this, 9, false), new MoveLadderTask(this, 1)));
			if(switchPos.equals("L")) { //if switch is on the same side
				autonomousQueue.add(new TurnTask(this, 90));  
				autonomousQueue.add(new MoveForwardTask(this, 3, false));
				autonomousQueue.add(new MoveForwardTask(this, 2, true)); //do nudge;
				autonomousQueue.add(new OpenClawTask(this));
			}
		} else if(ourPosition.equals("M")){
			int isRight = 1; //changes this to -1 if left.
			
			if(switchPos.equals("L")) isRight = -1; //we are going left
			if(switchPos.equals("R")) isRight = 1; //we are going right;
			
			autonomousQueue.add(new MultiTask(new MoveForwardTask(this, 4, false), new MoveLadderTask(this, 1))); //original measurement 4.4
			autonomousQueue.add(new TurnTask(this, 90 * isRight)); 
			autonomousQueue.add(new MoveForwardTask(this, 3, false)); 
			autonomousQueue.add(new TurnTask(this, -90 * isRight)); 
			autonomousQueue.add(new MoveForwardTask(this, 4, false));
			autonomousQueue.add(new MoveForwardTask(this, 3, true));
		} else if(ourPosition.equals("R")) {
			
			if(switchPos.equals("R")) { //if its on the same side
				autonomousQueue.add(
						new MultiTask(
								new QueuedTask(new MoveForwardTask(this, 9, false), new TurnTask(this, -90)),
								new MoveLadderTask(this, 1)
						)
				);
				autonomousQueue.add(new MoveForwardTask(this, 3, false));
				autonomousQueue.add(new MoveForwardTask(this, 2, true)); //do nudge;
				autonomousQueue.add(new OpenClawTask(this));
			} else { //if its not on the same side
				autonomousQueue.add(
						new MultiTask(
								new MoveForwardTask(this, 9, false),
								new MoveLadderTask(this, 1)
						)
				);
			}
		}
	}
	
	@Override
	public void autonomousPeriodic(){
		SmartDashboard.putString("Autonomous Queue: ", autonomousQueue.toString());
		if(!autonomousQueue.isEmpty()){
			if(autonomousQueue.peek().run()){
				autonomousQueue.poll();
			}
			displayOnSmartDashboard();	
		}
	}
	
	//=== Test ===\\
	@Override
	public void testInit(){
		autonomousQueue = new LinkedList<>();
		//autonomousQueue.add(new MoveLadderTask(this, 1));
		//autonomousQueue.add(new TurnTask(this, 180));
		//autonomousQueue.add(new TurnTask(this, -180));
		//autonomousQueue.add(new MultiTask(new MoveForwardTask(this, 3, false), new MoveLadderTask(this, 1)));
		autonomousQueue.add(new OpenClawTask(this));
	}
	
	
	@Override
	public void testPeriodic(){
		if(!autonomousQueue.isEmpty()){
			if(autonomousQueue.peek().run()){
				autonomousQueue.poll();
			}
			displayOnSmartDashboard();
			SmartDashboard.putString("Current Task: ", autonomousQueue.peek() == null ? "null" : autonomousQueue.peek().toString());
			
		}
	}
	
	//========================= Getters ================================\\
	
	public Controller getController() {
		return controller;
	}
	
	public Climber getClimber(){
		return climber;
	}
	
	public MecanumDrive getDrive() {
		return drive;
	}
	
	public Encoder getLeftEncoder(){
		return leftEncoder;
	}
	
	public Encoder getRightEncoder(){
		return rightEncoder;
	}
	
	public ADXRS450_Gyro getGyro(){
		return gyro;
	}
	
	public Ladder getLadder() {
		return ladder;
	}
	
	//==================So the talons dont keep moving in back to back matches==========//
	
	public void stopMotors() {
		ladder.stopClaw();
		ladder.stopLadder();
		climber.stop();
		drive.driveCartesian(0, 0, 0);
	}
	
	//========================SMART DASHBOARD STUFFS========================================//
	public void displayOnSmartDashboard(){
		
		// Realistic not needed for now
//		SmartDashboard.putString("Front Left: ", "" + frontLeft.get());
//		SmartDashboard.putString("Front Right: ", "" + frontRight.get());
//		SmartDashboard.putString("Rear Left: ", "" + rearLeft.get());
//		SmartDashboard.putString("Rear Right: ", "" + rearRight.get());
//		SmartDashboard.putString("Gyro Rate: ", "" + gyro.getRate());

		//actually needed now:		
		SmartDashboard.putString("Current Task: ", currentTask.toString());
		
		SmartDashboard.putNumber("Left Encoder: ", leftEncoder.getDistance());
		SmartDashboard.putNumber("Right Encoder: ", rightEncoder.getDistance());
		
		SmartDashboard.putNumber("Gyro: ", ((int)gyro.getAngle() + 180) % 360 - 180);	// put angle in range [-180, 180]
		
		SmartDashboard.putBoolean("Ladder Top Limit: ", topLimit.get());
		SmartDashboard.putBoolean("Ladder Bottom Limit: ", armBottomLimit.get());
		
		SmartDashboard.putBoolean("Moving", clawWillOpen ^ ladder.clawIsOpenedAllTheWayOrIsClosedAllTheWay());
		SmartDashboard.putBoolean("Fully Open Close", ladder.clawIsOpenedAllTheWayOrIsClosedAllTheWay());
		SmartDashboard.putBoolean("Claw Will Open", clawWillOpen);
		
		SmartDashboard.putString("Claw Power: ", "" + claw.get() + " " + (claw.get() < 0 ? "Opening": "Closing"));
		SmartDashboard.putNumber("Ladder Encoder: ", ladderEncoder.getDistance());	
		
		SmartDashboard.putNumber("Ladder Pos", ladder.getPos());	
	}
}