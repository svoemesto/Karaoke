/**
 * Pitch-shifting audio insert node using SoundTouch + ScriptProcessorNode.
 *
 * Compatible with KaraokePlayer's AudioBufferSourceNode → GainNode architecture.
 * Acts as an insert: source.connect(pitchShifter).connect(gainNode).
 *
 * @see docs/features/audio-transpose.md
 */

import { SoundTouch } from 'soundtouchjs'

/**
 * Create a pitch-shifting ScriptProcessorNode for an AudioContext.
 *
 * @param audioCtx - AudioContext
 * @param initialPitch - Pitch ratio, 1.0 = original
 * @param bufferSize - Processing buffer size (default 4096)
 * @returns ScriptProcessorNode with .setPitch(ratio) and .setPitchSemitones(st) methods
 */
export function createPitchShifterNode(audioCtx, initialPitch = 1.0, bufferSize = 4096) {
  const soundTouch = new SoundTouch()
  soundTouch.pitch = initialPitch

  const processor = audioCtx.createScriptProcessor(bufferSize, 2, 2)

  const inputChunk = new Float32Array(bufferSize * 2)
  const outputChunk = new Float32Array(bufferSize * 2)

  processor.onaudioprocess = (e) => {
    const inputL = e.inputBuffer.getChannelData(0)
    const inputR = e.inputBuffer.getChannelData(1)
    const outputL = e.outputBuffer.getChannelData(0)
    const outputR = e.outputBuffer.getChannelData(1)

    // Interleave input (L R L R…)
    for (let i = 0; i < bufferSize; i++) {
      inputChunk[i * 2] = inputL[i]
      inputChunk[i * 2 + 1] = inputR[i]
    }

    // Feed to SoundTouch via inputBuffer.putSamples
    soundTouch.inputBuffer.putSamples(inputChunk, 0, bufferSize)

    // Process (runs stretch/transposer chain)
    soundTouch.process()

    // Retrieve from outputBuffer.receiveSamples
    const received = soundTouch.outputBuffer.receiveSamples(outputChunk, bufferSize)

    // Deinterleave output
    const framesToCopy = Math.min(received, bufferSize)
    for (let i = 0; i < framesToCopy; i++) {
      outputL[i] = outputChunk[i * 2]
      outputR[i] = outputChunk[i * 2 + 1]
    }

    // Zero-fill underrun (buffer underflow)
    for (let i = framesToCopy; i < bufferSize; i++) {
      outputL[i] = 0.0
      outputR[i] = 0.0
    }
  }

  processor.setPitch = (ratio) => {
    soundTouch.pitch = ratio
  }

  processor.setPitchSemitones = (semitones) => {
    soundTouch.pitchSemitones = semitones
  }

  return processor
}
