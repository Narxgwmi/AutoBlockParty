package com.autoblockparty.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class AutoBlockPartyConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("AutoBlockPartyConfig");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "autoblockparty.json";

    public boolean enabledOnStart = false;
    public int scanRadius = 20;
    public boolean sprint = true;
    public double sprintMinDistance = 3.0;
    public boolean autoJump = true;
    public boolean bunnyHop = false;
    public double deadzone = 0.15;
    public int searchIntervalTicks = 5;
    public boolean showActionbarMessages = true;
    public boolean debug = false;

    public static Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    public static AutoBlockPartyConfig load() {
        Path path = getConfigPath();
        AutoBlockPartyConfig config;

        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                AutoBlockPartyConfig loaded = GSON.fromJson(reader, AutoBlockPartyConfig.class);
                if (loaded != null) {
                    loaded.clampToValidRanges();
                    loaded.save();
                    return loaded;
                }
            } catch (IOException | JsonParseException e) {
                LOGGER.warn("Failed to read {}. Recreating with defaults.", path, e);
            }
        }

        config = new AutoBlockPartyConfig();
        config.clampToValidRanges();
        config.save();
        return config;
    }

    public void save() {
        clampToValidRanges();
        Path path = getConfigPath();
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save config to {}", path, e);
        }
    }

    public void clampToValidRanges() {
        this.scanRadius = clampInt(this.scanRadius, 5, 50);
        this.sprintMinDistance = clampDouble(this.sprintMinDistance, 1.0, 10.0);
        this.deadzone = clampDouble(this.deadzone, 0.05, 0.5);
        this.searchIntervalTicks = clampInt(this.searchIntervalTicks, 1, 20);
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clampDouble(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}