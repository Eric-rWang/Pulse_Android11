package tan.philip.nrf_ble.Algorithms;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tan.philip.nrf_ble.Algorithms.Filter.Filter;
import tan.philip.nrf_ble.BLE.PacketParsing.SignalSetting;
import tan.philip.nrf_ble.GraphScreen.UIComponents.ValueAlert;

public class PanTompkinsAlgorithm extends Biometric {
    private static final String TAG = "PanTompkinsAlgorithm";

    private final int signalID;
    private Filter bandpassFilter;

    private final float SAMPLING_RATE;
    private final int REFRACTORY_PERIOD;
    private final int THRESHOLD_RESET_TIME;

    private float lastSample = 0;

    //private List<Integer> signalPeaks;
    private int[] signalPeaks = new int[9];
    private int numSignalPeaksDetected = 0;

    private float SPKI;
    private float NPKI;

    private float threshold_I1;
    private float threshold_I2;

    private int rrMissedLimit;
    private int index;
    private List<Integer> indexes;

    //If a signal peak is deemed to have been missed, a searchback between the last two peaks is
    //conducted. The largest amplitude peak in this time is considered to be a QRS candidate.
    private ArrayList<Float> noisePeakVals;
    private ArrayList<Integer> noisePeakLocations;


    private static final float MAX_QRS_DURATION = 0.150f; //seconds
    private final int movingAverageLength;
    private float[] movingAverageValues;
    private float[] localMaxValues = new float[2];
    private int numSamples = 0;

    public PanTompkinsAlgorithm(HashMap<Integer, SignalSetting> signalsInAlgorithm, byte index) {
        super(signalsInAlgorithm, index, "Pan-Tompkins");

        if (signalsInAlgorithm.size() > 1)
            Log.e(TAG, "Pan Tompkins Algorithm received an unexpected number ("+ signalsInAlgorithm.size() +") of signals.");

        //This algorithm should only use one signal, so pull the data from the first signal.
        Map.Entry<Integer,SignalSetting> firstSignal = signalsInAlgorithm.entrySet().iterator().next();
        this.signalID = firstSignal.getKey();

        this.SAMPLING_RATE = firstSignal.getValue().fs;
        this.REFRACTORY_PERIOD = (int) (0.3f * SAMPLING_RATE);
        this.THRESHOLD_RESET_TIME = (int) (2f * SAMPLING_RATE);

        this.movingAverageLength = (int) (MAX_QRS_DURATION * SAMPLING_RATE);
        this.movingAverageValues = new float[movingAverageLength];

        //signalPeaks = new ArrayList<>();
        noisePeakLocations = new ArrayList();
        noisePeakVals = new ArrayList();

        SPKI = 0.0f;
        NPKI = 0.0f;

        threshold_I1 = 0.0f;
        threshold_I2 = 0.0f;

        rrMissedLimit = 0;
        index = 0;
        indexes = new ArrayList<>();
    }

    @Override
    public void startAlgorithm() {
        //Check if the number of imported parameters for the bandpass filter is correct
        if(inputParameters.size() != 6) {
            Log.e(TAG, "Wrong number of parameters for Pan Tompkins algorithm.");
            return;
        }

        //First order filter is sufficient, should be from 5 to 15 Hz
        //Parameters go B0 to B2, A0 to A2
        double[] b = new double[3];
        double[] a = new double[3];

        for(int i = 0; i < 3; i++ )
            b[i] = inputParameters.get(i);
        for(int i = 0; i < 3; i++ )
            a[i] = inputParameters.get(i+3);

        bandpassFilter = new Filter(b, a, 1);

        super.startAlgorithm();
    }

    @Override
    public void computeAndDisplay(HashMap<Integer, ArrayList<Integer>> allNewData) {
        if(!this.algorithmReady)
            return;

        ArrayList<Integer> rawData = allNewData.get(signalID);

        for(float newSample : rawData) {
            processSample(newSample);
        }
    }

    private void processSample(float sample) {
        //Bandpass filter the signal
        float filteredSample = bandpassFilter.findNextY(sample);

        //Differentiate the signal
        float diffSig = filteredSample - lastSample;
        lastSample = filteredSample;

        //Square the signal
        float squaredSig = diffSig * diffSig;

        //Integrate the signal using a moving average filter
        float intSig = integrateSignal(squaredSig);

        //If the current sample is a local maximum, consider it as a candidate R peak
        if(isLocalMax(intSig)) {
            int peakLocation = numSamples - 1;
            float candidatePeakVal = localMaxValues[0];

            //If the peak value is above the first threshold
            //If the time between this peak and the last signal peak is over 300ms (paper uses 360 ms?)
            //If there aren't any signal peaks yet don't check the time
            if(candidatePeakVal > threshold_I1 && sufficientDistance(peakLocation)) {
                //Add the candidate peak as a signal peak
                //signalPeaks.add(peakLocation);
                signalPeaks[numSignalPeaksDetected % 9] = peakLocation;
                numSignalPeaksDetected ++;


                Log.d(TAG, "Beat detected, peak at " + ((float) peakLocation / SAMPLING_RATE) + "s");
                //??

                //Update the signal peak amplitude
                SPKI = 0.125f * candidatePeakVal + 0.875f * SPKI;

                //If we have more than 8 peaks, we can estimate the max reasonable time between peaks.
                //If the time between peaks has exceeded this limit...
                //Search between the last two established signal peaks
                //The largest amplitude peak between these points is considered a QRS candidate
                if(rrMissedLimit != 0 && (lastPeakLocation() - lastLastPeakLocation()) > rrMissedLimit) {
                    //if(rrMissedLimit != 0 && (signalPeaks.get(signalPeaks.size() - 2) - signalPeaks.get(signalPeaks.size() - 1)) > rrMissedLimit) {
                    //Check all recent noise peaks to see if they have enough distance from the current signal peak
                    //Also check if the noise peak value is of sufficient amplitude
                    //If the peak qualifies, keep track of the amplitude and location
                    float largestNoisePeakVal = -1;
                    int largestNoisePeakLocation = -1;

                    for (int i = noisePeakLocations.size() - 1; i >= 0; i --) {
                        if(peakLocation - noisePeakLocations.get(i) > REFRACTORY_PERIOD &&
                                noisePeakVals.get(i) > threshold_I2 &&
                                noisePeakVals.get(i) > largestNoisePeakVal) {
                            largestNoisePeakVal = noisePeakVals.get(i);
                            largestNoisePeakLocation = noisePeakLocations.get(i);
                        }
                    }

                    //The largest amplitude peak is the the last R peak, if it qualified
                    if(largestNoisePeakLocation != -1) {

                        //Move the most recent signal peak to the end
                        //signalPeaks.add(peakLocation);
                        signalPeaks[numSignalPeaksDetected % 9] = peakLocation;
                        numSignalPeaksDetected ++;

                        //Add the missed peak before the last peak
                        //signalPeaks.set(signalPeaks.size() - 2, largestNoisePeakLocation);
                        signalPeaks[(numSignalPeaksDetected - 2) % 9] = peakLocation;

                        Log.d(TAG, "Missed beat detected at " + ((float) peakLocation / SAMPLING_RATE) + "s");
                    }
                }

                //Reset the noise peak QRS candidates
                noisePeakLocations.clear();
                noisePeakVals.clear();

                //Log the IBI
                if(numSignalPeaksDetected >= 2) {
                    float ibi = (60f * (lastPeakLocation() - lastLastPeakLocation()) / SAMPLING_RATE);
                    Log.d(TAG, "HR: " + ibi + "bpm");

                    this.digitalDisplay.changeValue(ibi);

                    for(ValueAlert alert : alerts)
                        alert.checkValue(ibi);
                }
            }
            //If the peak value is NOT above threshold 1 or the time between peaks is not sufficient,
            //we consider this peak to be a noise peak
            else {
                NPKI = 0.125f * candidatePeakVal + 0.875f * NPKI;

                //Update the largest noise peak in case we need to consider it as a candidate
                //QRS complex.
                //Only consider it if there is sufficient distance from previous signal peak.
                if (sufficientDistance(peakLocation)) {
                    noisePeakLocations.add(peakLocation);
                    noisePeakVals.add(candidatePeakVal);
                }

                //If it's been a long time since a signal peak was detected, reset the thresholds.
                //A motion peak could have thrown off the thresholds and therefore needs recovery.
                if (peakLocation - lastPeakLocation() > THRESHOLD_RESET_TIME) {
                    SPKI = 0.0f;
                    NPKI = 0.0f;
                    noisePeakLocations.clear();
                    noisePeakVals.clear();
                    Log.d(TAG, "Pan-Tompkins threshold reset.");
                }
            }

            //Update both thresholds based on the signal and noise levels
            threshold_I1 = NPKI + 0.25f * (SPKI-NPKI);
            threshold_I2 = 0.5f * threshold_I1;

            //Update the RR missed limit
            if (numSignalPeaksDetected > 8) {
                int rr_avg = getLastEightRRsAvg();         //RR AVERAGE 2
                rrMissedLimit = (int) (1.66 * rr_avg);     //RR MISSED LIMIT
            }
        }

        numSamples++;

    }

    private int mean(int[] arr) {
        int sum = 0;
        for (int num : arr) {
            sum += num;
        }
        return sum / arr.length;
    }

    private float integrateSignal(float newSample) {
        //Integrate with moving average
        //First, replace an old value with the current value
        movingAverageValues[numSamples % movingAverageLength] = newSample;
        //Next, sum the values in the array
        float summedVal = 0;
        for (int i = 0; i < movingAverageLength; i ++) {
            summedVal += movingAverageValues[i];
        }
        return summedVal;
    }

    private boolean isLocalMax(float newSample) {
        //Compare last sample value with adjacent points
        boolean isMax = (localMaxValues[0] < localMaxValues[1]) && (localMaxValues[1] > newSample);

        //Update last two samples
        localMaxValues[0] = localMaxValues[1];
        localMaxValues[1] = newSample;

        return isMax;
    }

    private int getLastEightRRsAvg() {
        int[] rrs = new int[8];

        //Calculate the 8 RR intervals from the last 9 peaks
        for(int i = 0; i < 8; i ++) {
            rrs[i] = signalPeaks[i + 1] - signalPeaks[i];
        }

        //Calculate the average RR interval
        int rr_avg = mean(rrs);

        return rr_avg;
    }

    private boolean sufficientDistance(int peakLocation) {
        return (numSignalPeaksDetected == 0 || (peakLocation - lastPeakLocation()) > REFRACTORY_PERIOD);
    }

    private int lastPeakLocation() {
        return signalPeaks[(numSignalPeaksDetected - 1) % 9];
    }

    private int lastLastPeakLocation() {
        return signalPeaks[(numSignalPeaksDetected - 2) % 9];
    }
}