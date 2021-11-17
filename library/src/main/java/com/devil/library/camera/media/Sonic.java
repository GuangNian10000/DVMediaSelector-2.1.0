package com.devil.library.camera.media;

import java.nio.ShortBuffer;
import java.util.Arrays;

final class Sonic {

    private static final int MINIMUM_PITCH = 65;
    private static final int MAXIMUM_PITCH = 400;
    private static final int AMDF_FREQUENCY = 4000;

    private final int inputSampleRateHz;
    private final int numChannels;
    private final float speed;
    private final float pitch;
    private final float rate;
    private final int minPeriod;
    private final int maxPeriod;
    private final int maxRequired;
    private final short[] downSampleBuffer;

    private int inputBufferSize;
    private short[] inputBuffer;
    private int outputBufferSize;
    private short[] outputBuffer;
    private int pitchBufferSize;
    private short[] pitchBuffer;
    private int oldRatePosition;
    private int newRatePosition;
    private int numInputSamples;
    private int numOutputSamples;
    private int numPitchSamples;
    private int remainingInputToCopy;
    private int prevPeriod;
    private int prevMinDiff;
    private int minDiff;
    private int maxDiff;

    /**
     * Creates a new Sonic audio stream processor.
     *
     * @param inputSampleRateHz The sample rate of input audio, in hertz.
     * @param numChannels The number of channels in the input audio.
     * @param speed The speedup factor for output audio.
     * @param pitch The pitch factor for output audio.
     * @param outputSampleRateHz The sample rate for output audio, in hertz.
     */
    public Sonic(int inputSampleRateHz, int numChannels, float speed, float pitch,
                 int outputSampleRateHz) {
        this.inputSampleRateHz = inputSampleRateHz;
        this.numChannels = numChannels;
        minPeriod = inputSampleRateHz / MAXIMUM_PITCH;
        maxPeriod = inputSampleRateHz / MINIMUM_PITCH;
        maxRequired = 2 * maxPeriod;
        downSampleBuffer = new short[maxRequired];
        inputBufferSize = maxRequired;
        inputBuffer = new short[maxRequired * numChannels];
        outputBufferSize = maxRequired;
        outputBuffer = new short[maxRequired * numChannels];
        pitchBufferSize = maxRequired;
        pitchBuffer = new short[maxRequired * numChannels];
        oldRatePosition = 0;
        newRatePosition = 0;
        prevPeriod = 0;
        this.speed = speed;
        this.pitch = pitch;
        this.rate = (float) inputSampleRateHz / outputSampleRateHz;
    }

    /**
     * Queues remaining data from {@code buffer}, and advances its position by the number of bytes
     * consumed.
     *
     * @param buffer A {@link ShortBuffer} containing input data between its position and limit.
     */
    public void queueInput(ShortBuffer buffer) {
        int samplesToWrite = buffer.remaining() / numChannels;
        int bytesToWrite = samplesToWrite * numChannels * 2;
        enlargeInputBufferIfNeeded(samplesToWrite);
        buffer.get(inputBuffer, numInputSamples * numChannels, bytesToWrite / 2);
        numInputSamples += samplesToWrite;
        processStreamInput();
    }

    /**
     * Gets available output, outputting to the start of {@code buffer}. The buffer's position will be
     * advanced by the number of bytes written.
     *
     * @param buffer A {@link ShortBuffer} into which output will be written.
     */
    public void getOutput(ShortBuffer buffer) {
        int samplesToRead = Math.min(buffer.remaining() / numChannels, numOutputSamples);
        buffer.put(outputBuffer, 0, samplesToRead * numChannels);
        numOutputSamples -= samplesToRead;
        System.arraycopy(outputBuffer, samplesToRead * numChannels, outputBuffer, 0,
                numOutputSamples * numChannels);
    }

    /**
     * Forces generating output using whatever data has been queued already. No extra delay will be
     * added to the output, but flushing in the middle of words could introduce distortion.
     */
    public void queueEndOfStream() {
        int remainingSamples = numInputSamples;
        float s = speed / pitch;
        float r = rate * pitch;
        int expectedOutputSamples =
                numOutputSamples + (int) ((remainingSamples / s + numPitchSamples) / r + 0.5f);

        // Add enough silence to flush both input and pitch buffers.
        enlargeInputBufferIfNeeded(remainingSamples + 2 * maxRequired);
        for (int xSample = 0; xSample < 2 * maxRequired * numChannels; xSample++) {
            inputBuffer[remainingSamples * numChannels + xSample] = 0;
        }
        numInputSamples += 2 * maxRequired;
        processStreamInput();
        // Throw away any extra samples we generated due to the silence we added.
        if (numOutputSamples > expectedOutputSamples) {
            numOutputSamples = expectedOutputSamples;
        }
        // Empty input and pitch buffers.
        numInputSamples = 0;
        remainingInputToCopy = 0;
        numPitchSamples = 0;
    }

    /**
     * Returns the number of output samples that can be read with {@link #getOutput(ShortBuffer)}.
     */
    public int getSamplesAvailable() {
        return numOutputSamples;
    }

    // Internal methods.

    private void enlargeOutputBufferIfNeeded(int numSamples) {
        if (numOutputSamples + numSamples > outputBufferSize) {
            outputBufferSize += (outputBufferSize / 2) + numSamples;
            outputBuffer = Arrays.copyOf(outputBuffer, outputBufferSize * numChannels);
        }
    }

    private void enlargeInputBufferIfNeeded(int numSamples) {
        if (numInputSamples + numSamples > inputBufferSize) {
            inputBufferSize += (inputBufferSize / 2) + numSamples;
            inputBuffer = Arrays.copyOf(inputBuffer, inputBufferSize * numChannels);
        }
    }

    private void removeProcessedInputSamples(int position) {
        int remainingSamples = numInputSamples - position;
        System.arraycopy(inputBuffer, position * numChannels, inputBuffer, 0,
                remainingSamples * numChannels);
        numInputSamples = remainingSamples;
    }

    private void copyToOutput(short[] samples, int position, int numSamples) {
        enlargeOutputBufferIfNeeded(numSamples);
        System.arraycopy(samples, position * numChannels, outputBuffer, numOutputSamples * numChannels,
                numSamples * numChannels);
        numOutputSamples += numSamples;
    }

    private int copyInputToOutput(int position) {
        int numSamples = Math.min(maxRequired, remainingInputToCopy);
        copyToOutput(inputBuffer, position, numSamples);
        remainingInputToCopy -= numSamples;
        return numSamples;
    }

    private void downSampleInput(short[] samples, int position, int skip) {
        // If skip is greater than one, average skip samples together and write them to the down-sample
        // buffer. If numChannels is greater than one, mix the channels together as we down sample.
        int numSamples = maxRequired / skip;
        int samplesPerValue = numChannels * skip;
        position *= numChannels;
        for (int i = 0; i < numSamples; i++) {
            int value = 0;
            for (int j = 0; j < samplesPerValue; j++) {
                value += samples[position + i * samplesPerValue + j];
            }
            value /= samplesPerValue;
            downSampleBuffer[i] = (short) value;
        }
    }

    private int findPitchPeriodInRange(short[] samples, int position, int minPeriod, int maxPeriod) {
        // Find the best frequency match in the range, and given a sample skip multiple. For now, just
        // find the pitch of the first channel.
        int bestPeriod = 0;
        int worstPeriod = 255;
        int minDiff = 1;
        int maxDiff = 0;
        position *= numChannels;
        for (int period = minPeriod; period <= maxPeriod; period++) {
            int diff = 0;
            for (int i = 0; i < period; i++) {
                short sVal = samples[position + i];
                short pVal = samples[position + period + i];
                diff += Math.abs(sVal - pVal);
            }
            // Note that the highest number of samples we add into diff will be less than 256, since we
            // skip samples. Thus, diff is a 24 bit number, and we can safely multiply by numSamples
            // without overflow.
            if (diff * bestPeriod < minDiff * period) {
                minDiff = diff;
                bestPeriod = period;
            }
            if (diff * worstPeriod > maxDiff * period) {
                maxDiff = diff;
                worstPeriod = period;
            }
        }
        this.minDiff = minDiff / bestPeriod;
        this.maxDiff = maxDiff / worstPeriod;
        return bestPeriod;
    }

    /**
     * Returns whether the previous pitch period estimate is a better approximation, which can occur
     * at the abrupt end of voiced words.
     */
    private boolean previousPeriodBetter(int minDiff, int maxDiff, boolean preferNewPeriod) {
        if (minDiff == 0 || prevPeriod == 0) {
            return false;
        }
        if (preferNewPeriod) {
            if (maxDiff > minDiff * 3) {
                // Got a reasonable match this period
                return false;
            }
            if (minDiff * 2 <= prevMinDiff * 3) {
                // Mismatch is not that much greater this period
                return false;
            }
        } else {
            if (minDiff <= prevMinDiff) {
                return false;
            }
        }
        return true;
    }

    private int findPitchPeriod(short[] samples, int position, boolean preferNewPeriod) {
        // Find the pitch period. This is a critical step, and we may have to try multiple ways to get a
        // good answer. This version uses AMDF. To improve speed, we down sample by an integer factor
        // get in the 11 kHz range, and then do it again with a narrower frequency range without down
        // sampling.
        int period;
        int retPeriod;
        int skip = inputSampleRateHz > AMDF_FREQUENCY ? inputSampleRateHz / AMDF_FREQUENCY : 1;
        if (numChannels == 1 && skip == 1) {
            period = findPitchPeriodInRange(samples, position, minPeriod, maxPeriod);
        } else {
            downSampleInput(samples, position, skip);
            period = findPitchPeriodInRange(downSampleBuffer, 0, minPeriod / skip, maxPeriod / skip);
            if (skip != 1) {
                period *= skip;
                int minP = period - (skip * 4);
                int maxP = period + (skip * 4);
                if (minP < minPeriod) {
                    minP = minPeriod;
                }
                if (maxP > maxPeriod) {
                    maxP = maxPeriod;
                }
                if (numChannels == 1) {
                    period = findPitchPeriodInRange(samples, position, minP, maxP);
                } else {
                    downSampleInput(samples, position, 1);
                    period = findPitchPeriodInRange(downSampleBuffer, 0, minP, maxP);
                }
            }
        }
        if (previousPeriodBetter(minDiff, maxDiff, preferNewPeriod)) {
            retPeriod = prevPeriod;
        } else {
            retPeriod = period;
        }
        prevMinDiff = minDiff;
        prevPeriod = period;
        return retPeriod;
    }

    private void moveNewSamplesToPitchBuffer(int originalNumOutputSamples) {
        int numSamples = numOutputSamples - originalNumOutputSamples;
        if (numPitchSamples + numSamples > pitchBufferSize) {
            pitchBufferSize += (pitchBufferSize / 2) + numSamples;
            pitchBuffer = Arrays.copyOf(pitchBuffer, pitchBufferSize * numChannels);
        }
        System.arraycopy(outputBuffer, originalNumOutputSamples * numChannels, pitchBuffer,
                numPitchSamples * numChannels, numSamples * numChannels);
        numOutputSamples = originalNumOutputSamples;
        numPitchSamples += numSamples;
    }

    private void removePitchSamples(int numSamples) {
        if (numSamples == 0) {
            return;
        }
        System.arraycopy(pitchBuffer, numSamples * numChannels, pitchBuffer, 0,
                (numPitchSamples - numSamples) * numChannels);
        numPitchSamples -= numSamples;
    }

    private short interpolate(short[] in, int inPos, int oldSampleRate, int newSampleRate) {
        short left = in[inPos];
        short right = in[inPos + numChannels];
        int position = newRatePosition * oldSampleRate;
        int leftPosition = oldRatePosition * newSampleRate;
        int rightPosition = (oldRatePosition + 1) * newSampleRate;
        int ratio = rightPosition - position;
        int width = rightPosition - leftPosition;
        return (short) ((ratio * left + (width - ratio) * right) / width);
    }

    private void adjustRate(float rate, int originalNumOutputSamples) {
        if (numOutputSamples == originalNumOutputSamples) {
            return;
        }
        int newSampleRate = (int) (inputSampleRateHz / rate);
        int oldSampleRate = inputSampleRateHz;
        // Set these values to help with the integer math.
        while (newSampleRate > (1 << 14) || oldSampleRate > (1 << 14)) {
            newSampleRate /= 2;
            oldSampleRate /= 2;
        }
        moveNewSamplesToPitchBuffer(originalNumOutputSamples);
        // Leave at least one pitch sample in the buffer.
        for (int position = 0; position < numPitchSamples - 1; position++) {
            while ((oldRatePosition + 1) * newSampleRate > newRatePosition * oldSampleRate) {
                enlargeOutputBufferIfNeeded(1);
                for (int i = 0; i < numChannels; i++) {
                    outputBuffer[numOutputSamples * numChannels + i] =
                            interpolate(pitchBuffer, position * numChannels + i, oldSampleRate, newSampleRate);
                }
                newRatePosition++;
                numOutputSamples++;
            }
            oldRatePosition++;
            if (oldRatePosition == oldSampleRate) {
                oldRatePosition = 0;
                if (newRatePosition != newSampleRate) {
                    throw new IllegalStateException();
                }
                newRatePosition = 0;
            }
        }
        removePitchSamples(numPitchSamples - 1);
    }

    private int skipPitchPeriod(short[] samples, int position, float speed, int period) {
        // Skip over a pitch period, and copy period/speed samples to the output.
        int newSamples;
        if (speed >= 2.0f) {
            newSamples = (int) (period / (speed - 1.0f));
        } else {
            newSamples = period;
            remainingInputToCopy = (int) (period * (2.0f - speed) / (speed - 1.0f));
        }
        enlargeOutputBufferIfNeeded(newSamples);
        overlapAdd(newSamples, numChannels, outputBuffer, numOutputSamples, samples, position, samples,
                position + period);
        numOutputSamples += newSamples;
        return newSamples;
    }

    private int insertPitchPeriod(short[] samples, int position, float speed, int period) {
        // Insert a pitch period, and determine how much input to copy directly.
        int newSamples;
        if (speed < 0.5f) {
            newSamples = (int) (period * speed / (1.0f - speed));
        } else {
            newSamples = period;
            remainingInputToCopy = (int) (period * (2.0f * speed - 1.0f) / (1.0f - speed));
        }
        enlargeOutputBufferIfNeeded(period + newSamples);
        System.arraycopy(samples, position * numChannels, outputBuffer, numOutputSamples * numChannels,
                period * numChannels);
        overlapAdd(newSamples, numChannels, outputBuffer, numOutputSamples + period, samples,
                position + period, samples, position);
        numOutputSamples += period + newSamples;
        return newSamples;
    }

    private void changeSpeed(float speed) {
        if (numInputSamples < maxRequired) {
            return;
        }
        int numSamples = numInputSamples;
        int position = 0;
        do {
            if (remainingInputToCopy > 0) {
                position += copyInputToOutput(position);
            } else {
                int period = findPitchPeriod(inputBuffer, position, true);
                if (speed > 1.0) {
                    position += period + skipPitchPeriod(inputBuffer, position, speed, period);
                } else {
                    position += insertPitchPeriod(inputBuffer, position, speed, period);
                }
            }
        } while (position + maxRequired <= numSamples);
        removeProcessedInputSamples(position);
    }

    private void processStreamInput() {
        // Resample as many pitch periods as we have buffered on the input.
        int originalNumOutputSamples = numOutputSamples;
        float s = speed / pitch;
        float r = rate * pitch;
        if (s > 1.00001 || s < 0.99999) {
            changeSpeed(s);
        } else {
            copyToOutput(inputBuffer, 0, numInputSamples);
            numInputSamples = 0;
        }
        if (r != 1.0f) {
            adjustRate(r, originalNumOutputSamples);
        }
    }

    private static void overlapAdd(int numSamples, int numChannels, short[] out, int outPos,
                                   short[] rampDown, int rampDownPos, short[] rampUp, int rampUpPos) {
        for (int i = 0; i < numChannels; i++) {
            int o = outPos * numChannels + i;
            int u = rampUpPos * numChannels + i;
            int d = rampDownPos * numChannels + i;
            for (int t = 0; t < numSamples; t++) {
                out[o] = (short) ((rampDown[d] * (numSamples - t) + rampUp[u] * t) / numSamples);
                o += numChannels;
                d += numChannels;
                u += numChannels;
            }
        }
    }

}

