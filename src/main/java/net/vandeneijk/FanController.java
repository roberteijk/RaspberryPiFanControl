/**
 * I designed this simple app to control a cooling fan on my Raspberry Pi 4B to prevent throttling. It is designed to let the fan sit idle most of
 * the time. With the right parameters, it will only turn on under sustained CPU load or to offload dust buildup. The later requires a special fan
 * and case design to work properly.
 *
 * @author Robert van den Eijk.
 */

package net.vandeneijk;

import com.pi4j.io.gpio.*;
import com.pi4j.system.SystemInfo;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;



public class FanController {

    // Class variables with setup values.
    private static final GpioController mGpio = GpioFactory.getInstance();
    private static final GpioPinDigitalOutput mFanPin = mGpio.provisionDigitalOutputPin(RaspiPin.GPIO_00, PinState.LOW);
    private static double cpuTemp = 67; // A safe temporary value that doesn't trigger any action until the first measurement.
    private static double minTemp = 50;
    private static double maxTemp = 75;
    private static long minDuration = 300_000;
    private static long maxIntervalFanSpins = 0; // 0 = disabled. Can be used the purge dust buildup surrounding the fan.

    // Class variables to keep track of program state.
    private static long fanLastRun;
    private static boolean fanRunning;
    private static boolean welcomeDisplayed;



    public static void main(String[] args) {
        testArgs(args);
        printInfo();

        while (true) {
            LocalDateTime ldt = LocalDateTime.now();

            try {
                Thread.sleep(1000);
                ldt = LocalDateTime.now();
                cpuTemp = SystemInfo.getCpuTemperature();
            } catch (IOException | InterruptedException miscEx) {
                System.out.println(ldt + "  -  Error, temperature not measured.");
            }

            String info = ldt + "  -  " + cpuTemp + "c  :  ";
            if (!welcomeDisplayed) {
                System.out.println(info + "Measurement started.");
                welcomeDisplayed = true;
            }

            if (!fanRunning &&  maxIntervalFanSpins > 0 && fanLastRun + maxIntervalFanSpins < System.currentTimeMillis()) fanController(true, info + "Fan started for daily anti-dust run.");
            if (!fanRunning && cpuTemp >= maxTemp) fanController(true, info + "Fan started because temp is too high!!!");
            if (fanRunning && fanLastRun + minDuration < System.currentTimeMillis() && cpuTemp <= minTemp) fanController(false, info + "Fan stopped. Everything is ok.");
        }
    }

    private static void testArgs(String[] args) {
        double minTempLowest = 30;
        double minTempHighest = 65;
        double maxTempLowest = 70;
        double maxTempHighest = 82;
        long minDurationLowest = 0;
        long maxIntervalFanSpinsLowest = 0;

        List<String> faultyArgs = new ArrayList<>();



        for (String arg : args) {
            String[] currentArg = arg.split("=");
            if (currentArg.length != 2) {
                faultyArgs.add(arg);
                continue;
            }
            try {
                switch(currentArg[0]) {
                    case "min":
                        minTemp = Double.parseDouble(currentArg[1]);
                        if (minTemp < minTempLowest || minTemp > minTempHighest) faultyArgs.add(arg + "  Value is outside the accepted range (" + minTempLowest + " - " + minTempHighest + ").");
                        break;
                    case "max":
                        maxTemp = Double.parseDouble(currentArg[1]);
                        if (maxTemp < maxTempLowest || maxTemp > maxTempHighest) faultyArgs.add(arg + "  Value is outside the accepted range (" + maxTempLowest + " - " + maxTempHighest + ").");
                        break;
                    case "dur":
                        minDuration = Long.parseLong(currentArg[1]) * 1000;
                        if (minDuration < minDurationLowest) faultyArgs.add(arg + "  Value must be " + minDurationLowest + " or higher.");
                        break;
                    case "dus":
                        maxIntervalFanSpins = Long.parseLong(currentArg[1]) * 3_600_000;
                        if (maxIntervalFanSpins < maxIntervalFanSpinsLowest) faultyArgs.add(arg + "  Value must be " + maxIntervalFanSpinsLowest + " or higher.");
                        break;
                    default:
                        faultyArgs.add(arg);
                }
            } catch (NumberFormatException nfEx) {
                faultyArgs.add(arg);
            }
        }

        if (faultyArgs.size() != 0) printHelp(faultyArgs);
    }

    /**
     * The printHelp method uses several println statements instead of new line characters. According to documentation, this is more robust because
     * some Java VM's may have their own interpretation of these special characters.
     */
    private static void printHelp(List<String> notes) {
        System.out.println();
        System.out.println("Error, the following parameters are not recognized:");
        System.out.println();
        for (String note : notes) {
            System.out.println(" " + note);
        }
        System.out.println();
        System.out.println();
        System.out.println("Parameters:");
        System.out.println();
        System.out.println("  min=<value> = Temp (c) at which the fan will turn off.");
        System.out.println("  max=<value> = Temp (c) at which the fan will turn on.");
        System.out.println("  dur=<value> = Minimum duration (seconds) the fan runs.");
        System.out.println("  dus=<value< = Max time (hours) between fan spins. Used to remove dust.");
        System.out.println();
        System.out.println("Parameter usage example:");
        System.out.println();
        System.out.println("  min=45 max=82 dur=600 dus=24");
        System.out.println();
        System.out.println();

        System.exit(1);
    }

    /**
     * The printInfo method uses several println statements instead of new line characters. According to documentation, this is more robust because
     * some Java VM's may have their own interpretation of these special characters.
     */
    private static void printInfo() {
        System.out.println();
        System.out.println("Raspberry Pi Fan Controller v1.1.0");
        System.out.println();
        System.out.println(" developed by: Robert van den Eijk");
        System.out.println(" fan control pin (Pi4J): " + mFanPin.getPin());
        System.out.println(" fan lower threshold: " + minTemp + "c.");
        System.out.println(" fan upper threshold: " + maxTemp + "c.");
        System.out.println(" fan run duration: " + (minDuration / 1000) + "s.");
        System.out.println(" fan max spin interval: " + (maxIntervalFanSpins == 0 ? "disabled." : ((maxIntervalFanSpins / 3_600_000) + "h.")));
        System.out.println();
        System.out.println();
    }

    private static void fanController(boolean startFan, String textForConsole) {
        fanLastRun = System.currentTimeMillis();
        fanRunning = startFan;
        System.out.println(textForConsole);

        if (startFan) mFanPin.setState(PinState.HIGH);
        else mFanPin.setState(PinState.LOW);
    }
}
