package ovh.paulem.mc;

import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALCapabilities;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.lwjgl.stb.STBVorbis.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class SoundPlayer {
    private long device;
    private long context;
    private int buffer = 0;
    private int source = 0;

    public SoundPlayer() {
        device = ALC10.alcOpenDevice((ByteBuffer) null);
        if (device == NULL) throw new IllegalStateException("Impossible d'ouvrir le device audio");
        context = ALC10.alcCreateContext(device, (IntBuffer) null);
        if (context == NULL) throw new IllegalStateException("Impossible de créer le contexte audio");
        ALC10.alcMakeContextCurrent(context);
        AL.createCapabilities(ALC.createCapabilities(device));
    }

    public void play(String resourcePath) {
        stop();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Lecture du fichier OGG depuis les ressources dans un ByteBuffer
            try (var is = getClass().getResourceAsStream(resourcePath)) {
                if (is == null) throw new IOException("Fichier audio non trouvé : " + resourcePath);
                byte[] bytes = is.readAllBytes();
                ByteBuffer data = MemoryUtil.memAlloc(bytes.length);
                data.put(bytes).flip();
                IntBuffer channelsBuffer = stack.mallocInt(1);
                IntBuffer sampleRateBuffer = stack.mallocInt(1);
                ShortBuffer rawAudioBuffer = stb_vorbis_decode_memory(data, channelsBuffer, sampleRateBuffer);
                MemoryUtil.memFree(data);
                if (rawAudioBuffer == null) throw new IOException("Erreur de décodage OGG : " + resourcePath);
                int channels = channelsBuffer.get(0);
                int sampleRate = sampleRateBuffer.get(0);
                int format = channels == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;
                buffer = AL10.alGenBuffers();
                AL10.alBufferData(buffer, format, rawAudioBuffer, sampleRate);
                source = AL10.alGenSources();
                AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
                AL10.alSourcef(source, AL10.AL_GAIN, 0.3f); // Volume réduit
                AL10.alSourcePlay(source);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        if (source != 0) {
            AL10.alSourceStop(source);
            AL10.alDeleteSources(source);
            source = 0;
        }
        if (buffer != 0) {
            AL10.alDeleteBuffers(buffer);
            buffer = 0;
        }
    }

    public void cleanup() {
        stop();
        ALC10.alcDestroyContext(context);
        ALC10.alcCloseDevice(device);
    }
}
