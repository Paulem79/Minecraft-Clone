package ovh.paulem.mc;

import javazoom.jl.player.Player;
import java.io.BufferedInputStream;
import java.io.InputStream;

public class SoundPlayer {
    private Player player;
    private Thread playThread;

    public void play(String resourcePath) {
        stop();
        playThread = new Thread(() -> {
            try (InputStream is = getClass().getResourceAsStream(resourcePath);
                 BufferedInputStream bis = new BufferedInputStream(is)) {
                player = new Player(bis);
                player.play();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        playThread.setDaemon(true);
        playThread.start();
    }

    public void stop() {
        if (player != null) {
            player.close();
        }
        if (playThread != null && playThread.isAlive()) {
            playThread.interrupt();
        }
    }
}

