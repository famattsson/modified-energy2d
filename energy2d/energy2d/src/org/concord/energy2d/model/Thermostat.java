package org.concord.energy2d.model;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;

import java.awt.geom.Rectangle2D;
import java.io.*;

/**
 * A thermostat switches heating or cooling devices on or off to maintain the temperature at a setpoint.
 * 
 * A thermostat can be controlled by a thermometer, or just by the temperature at the center of the power source.
 * 
 * @author Charles Xie
 * 
 */
public class Thermostat {

	float reset = 0;
	float lastError = 0;
	private Thermometer thermometer;
	private Part powerSource;
	private float setpoint = 20;
	private float deadband = 1;
	float K = 50f;


	public Thermostat(Part powerSource) {
		if (powerSource == null)
			throw new IllegalArgumentException("A thermostat must be connected to a power source.");
		this.powerSource = powerSource;
	}

	public Thermostat(Thermometer thermometer, Part powerSource) {
		this(powerSource);
		this.thermometer = thermometer;
	}

	/** ORIGINAL: on/off (bang-bang) control, return a boolean to indicate if it is on or off. FILIP: I've modified this function to serve my experiments */
	public boolean onoff(Model2D model) {

		//The current power will always be equivalent to the power at the previous regulation time-step. Since this is the only place it can change.
		float power = powerSource.getPower();
		float t = 0;

		if (thermometer != null) {
			t = thermometer.getCurrentData();
		} else {
			Rectangle2D bounds = powerSource.getShape().getBounds2D();
			t = model.getTemperatureAt((float) bounds.getCenterX(), (float) bounds.getCenterY());
		}

		WriteDataToCSV(t, power, model, "C:\\Exjobbsimuleringsdata\\RFR_data.csv");

		//**** USE FOR PID REGULATOR EXPERIMENT ****//

		//powerSource.setPower(PidReg(model, t));

		//**** USE FOR MODEL INFERENCE EXPERIMENT ****//

		if(model.getTime()%36000 == 0) {
			setpoint = (setpoint+10)%40;
			System.out.println("set setpoint to: " + setpoint);
		}
		double nextPower = GetPredictedPower(t, power, model);

		if(nextPower!=Math.PI) {
			powerSource.setPower((float)nextPower);
			System.out.println("set power to: " + nextPower);
		}

		if(model.getTime() >= 7*24*3600) {
			System.exit(1);
		}



		return true;
	}

	public double GetPredictedPower(float t, float power, Model2D model) {

		Thermometer extTherm = model.getThermometer("External");
		float extTemp = extTherm.getCurrentData();
		float time = model.getTime();

		try {
			//Unirest.setTimeouts(0, 0);
			HttpResponse<String> response = Unirest.post("http://127.0.0.1:5000/RFRPred")
					.header("Content-Type", "application/json")
					.body("{\n\t\"ExternalTemperature\":["+extTemp+"],\n\t\"InternalTemperature\": ["+t+"],\n\t\"SetPointTemp\":["+setpoint+"]," +
							"\n\t\"Time\":["+time+"],\n\t\"RegulatorPower-1\":["+power+"]\n}")
					.asString();
			try {
				float responsePower = Float.parseFloat(response.getBody());
				return responsePower;
			} catch (NumberFormatException e) {
				return Math.PI;
			}
		} catch (UnirestException e) {
			e.printStackTrace();
			return Math.PI;
		}
	}

	public void WriteDataToCSV(float t, float power, Model2D model, String fileName) {
		try {
			Thermometer extTherm = model.getThermometer("External");

			BufferedReader br = new BufferedReader(new FileReader(fileName));
			FileWriter csvWriter = new FileWriter(fileName, true);
			String line = br.readLine();
			if (line == null || line.equals("")) {
				csvWriter.append("RegulatorPower,");
				csvWriter.append("ExternalTemperature,");
				csvWriter.append("InternalTemperature,");
				csvWriter.append("SetPointTemp,");
				csvWriter.append("Time\n");
			}
			csvWriter.append(power + ","); //Regulator Power
			csvWriter.append(extTherm.getCurrentData() + ","); //External Temp
			csvWriter.append(t + ",");//Internal Temp
			csvWriter.append(setpoint + ",");//Set point
			csvWriter.append(model.getTime() + "\n");//Time
			br.close();
			csvWriter.flush();
			csvWriter.close();

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public float PidReg(Model2D model, float t)  {
		float error = setpoint - t;
		float tau_i = model.getTimeStep();
		reset += K / tau_i * error;
		float derivative = K * tau_i * (error - lastError);
		float output = (K * error) + reset + derivative;
		lastError = error;
		return output;
	}

	public Thermometer getThermometer() {
		return thermometer;
	}

	public Part getPowerSource() {
		return powerSource;
	}

	public void setDeadband(float deadband) {
		this.deadband = deadband;
	}

	public float getDeadband() {
		return deadband;
	}

	public void setSetPoint(float setpoint) {
		this.setpoint = setpoint;
	}

	public float getSetPoint() {
		return setpoint;
	}

	public String toXml() {
		String xml = "<thermostat";
		xml += " set_point=\"" + setpoint + "\"";
		xml += " deadband=\"" + deadband + "\"";
		if (thermometer != null)
			xml += " thermometer=\"" + thermometer.getUid() + "\"";
		xml += " power_source=\"" + powerSource.getUid() + "\"/>";
		return xml;
	}

}
