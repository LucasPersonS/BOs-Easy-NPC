package de.markusbordihn.easynpc.paravexia.scene;

import de.markusbordihn.easynpc.config.Config;
import java.io.File;
import java.util.Properties;

/** Client side scene settings, stored in config/easy_npc/scene_client.cfg. */
@SuppressWarnings({"java:S1104", "java:S1444", "java:S3008"})
public class SceneClientConfig extends Config {

  public static final String CONFIG_FILE_NAME = "scene_client.cfg";
  public static final String CONFIG_FILE_HEADER =
"""
Paravexia Scene Client Configuration

animationFreezeDistance: Beyond this many blocks a scene stops animating and holds its last pose, 0 = always animate (default: 48)
ambienceEnabled: Play the background loops of scenes and ambience markers (market chatter, village murmur) (default: true)
ambienceVolumePercent: Volume of those background loops in percent (default: 100)

""";

  public static int ANIMATION_FREEZE_DISTANCE = 48;
  public static boolean AMBIENCE_ENABLED = true;
  public static int AMBIENCE_VOLUME_PERCENT = 100;

  private SceneClientConfig() {}

  public static void registerConfig() {
    registerConfigFile(CONFIG_FILE_NAME, CONFIG_FILE_HEADER);
    parseConfigFile();
  }

  public static void parseConfigFile() {
    File configFile = getConfigFile(CONFIG_FILE_NAME);
    if (configFile == null) {
      return;
    }
    Properties properties = readConfigFile(configFile);
    Properties unmodifiedProperties = (Properties) properties.clone();

    ANIMATION_FREEZE_DISTANCE =
        parseConfigValue(properties, "animationFreezeDistance", ANIMATION_FREEZE_DISTANCE, 0, 512);
    AMBIENCE_ENABLED = parseConfigValue(properties, "ambienceEnabled", AMBIENCE_ENABLED);
    AMBIENCE_VOLUME_PERCENT =
        parseConfigValue(properties, "ambienceVolumePercent", AMBIENCE_VOLUME_PERCENT, 0, 200);

    updateConfigFileIfChanged(configFile, CONFIG_FILE_HEADER, properties, unmodifiedProperties);
  }
}
