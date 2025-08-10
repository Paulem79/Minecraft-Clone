package ovh.paulem.mc;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Dirs {
    public static final Path MC = Paths.get(System.getProperty("user.dir"), ".mc-clone");
    public static final Path WORLD = Paths.get(MC.toString(), "worlds");
    public static final Path CONFIG = Paths.get(MC.toString(), "config");
}
