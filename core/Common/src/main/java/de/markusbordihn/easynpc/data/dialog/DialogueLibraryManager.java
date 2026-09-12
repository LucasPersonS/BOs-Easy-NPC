package de.markusbordihn.easynpc.data.dialog;

import de.markusbordihn.easynpc.Constants;
import de.markusbordihn.easynpc.data.action.ActionDataType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Loads reusable dialogue trees from config/easy_npc/dialogues. Library values are always returned
 * as deep copies, so hundreds of NPCs can share one source file without sharing mutable state.
 */
public final class DialogueLibraryManager {
  public static final String FILE_SUFFIX = ".dialog.snbt";
  public static final Path DIRECTORY = Constants.CONFIG_DIR.resolve("easy_npc").resolve("dialogues");
  private static final int MAX_FILE_BYTES = 1_048_576;
  private static final Logger log = LogManager.getLogger(Constants.LOG_NAME);
  private static volatile Map<ResourceLocation, DialogDataSet> libraries = Map.of();
  private static volatile boolean loadedOnce;

  private DialogueLibraryManager() {}

  public static synchronized LoadResult reload() {
    TreeMap<ResourceLocation, DialogDataSet> loaded = new TreeMap<>();
    int rejected = 0;
    try {
      Files.createDirectories(DIRECTORY);
      try (Stream<Path> files = Files.walk(DIRECTORY, 8)) {
        for (Path file : files.filter(Files::isRegularFile)
            .filter(path -> path.getFileName().toString().endsWith(FILE_SUFFIX)).sorted().toList()) {
          try {
            ResourceLocation fallbackId = idFromPath(DIRECTORY, file);
            Library library = parse(Files.readString(file, StandardCharsets.UTF_8), fallbackId);
            if (loaded.putIfAbsent(library.id(), library.dialogues()) != null) {
              throw new IllegalArgumentException("duplicate library id " + library.id());
            }
          } catch (Exception exception) {
            rejected++;
            log.error("Unable to load dialogue library {}: {}", file, exception.getMessage());
          }
        }
      }
    } catch (IOException exception) {
      log.error("Unable to scan dialogue library directory {}", DIRECTORY, exception);
      rejected++;
    }
    libraries = Collections.unmodifiableMap(loaded);
    loadedOnce = true;
    return new LoadResult(loaded.size(), rejected);
  }

  public static Optional<DialogDataSet> copy(ResourceLocation id) {
    ensureLoaded();
    DialogDataSet source = libraries.get(id);
    return source == null ? Optional.empty() : Optional.of(new DialogDataSet(source.createTag()));
  }

  public static Map<ResourceLocation, DialogDataSet> entries() {
    ensureLoaded();
    return libraries;
  }

  private static void ensureLoaded() {
    if (!loadedOnce) reload();
  }

  static Library parse(String content, ResourceLocation fallbackId) throws Exception {
    if (content == null || content.isBlank()) throw new IllegalArgumentException("empty file");
    if (content.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES)
      throw new IllegalArgumentException("file exceeds 1 MiB");
    CompoundTag root = TagParser.parseTag(content);
    ResourceLocation id = fallbackId;
    if (root.contains("Id", Tag.TAG_STRING)) {
      id = ResourceLocation.tryParse(root.getString("Id"));
      if (id == null) throw new IllegalArgumentException("invalid Id");
    }
    CompoundTag data = root.contains(DialogDataCapableTag.NAME, Tag.TAG_COMPOUND)
        ? root.getCompound(DialogDataCapableTag.NAME) : root;
    DialogDataSet dialogues = new DialogDataSet(data);
    if (!dialogues.hasDialog()) throw new IllegalArgumentException("no valid dialogues");
    int declaredDialogs = data.getList(DialogDataSet.DATA_DIALOG_DATA_SET_TAG, Tag.TAG_COMPOUND).size();
    if (declaredDialogs != dialogues.size())
      throw new IllegalArgumentException("duplicate or invalid dialogue labels");
    validateReferences(dialogues);
    dialogues.setLibraryId(id.toString());
    return new Library(id, dialogues);
  }

  static ResourceLocation idFromPath(Path root, Path file) {
    String relative = root.relativize(file).toString().replace('\\', '/');
    relative = relative.substring(0, relative.length() - FILE_SUFFIX.length());
    int slash = relative.indexOf('/');
    String namespace = slash > 0 ? relative.substring(0, slash) : Constants.MOD_ID;
    String path = slash > 0 ? relative.substring(slash + 1) : relative;
    ResourceLocation id = ResourceLocation.tryParse(namespace.toLowerCase() + ":" + path.toLowerCase());
    if (id == null) throw new IllegalArgumentException("invalid library path " + relative);
    return id;
  }

  private static void validateReferences(DialogDataSet dialogues) {
    for (DialogDataEntry dialog : dialogues.getDialogsByLabel()) {
      for (DialogButtonEntry button : dialog.getDialogButtons()) {
        for (var action : button.actionDataSet().getOrderedEntries()) {
          if ((action.actionDataType() == ActionDataType.OPEN_NAMED_DIALOG
              || action.actionDataType() == ActionDataType.OPEN_NAMED_DIALOG_CONDITIONAL)
              && !dialogues.hasDialog(action.command())) {
            throw new IllegalArgumentException("button " + button.label() + " in "
                + dialog.getLabel() + " references missing dialogue " + action.command());
          }
        }
      }
    }
  }

  /** Avoids a dependency cycle from the data package back to the entity interface. */
  private static final class DialogDataCapableTag {
    private static final String NAME = "DialogData";
  }

  public record LoadResult(int loaded, int rejected) {}
  record Library(ResourceLocation id, DialogDataSet dialogues) {}
}
