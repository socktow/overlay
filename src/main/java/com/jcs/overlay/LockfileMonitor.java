package com.jcs.overlay;

import com.jcs.overlay.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;

import static java.nio.file.StandardWatchEventKinds.*;

public class LockfileMonitor implements Runnable {
    private final Logger logger = LoggerFactory.getLogger(LockfileMonitor.class);

    public synchronized void setLeagueStarted(boolean leagueStarted) {
        this.leagueStarted = leagueStarted;
    }

    private boolean leagueStarted;

    @Override
    public void run() {
        WatchService watchService;
        Path leagueFolderPath;
        try {
            watchService = FileSystems.getDefault().newWatchService();
            leagueFolderPath = Utils.getLeagueDirectory().toPath();
            leagueFolderPath.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        Path lockfilePath = leagueFolderPath.resolve("lockfile");

        logger.info("Bienvenue ! En attente de connexion au jeu...");

        // On vérifie si le jeu est déjà démarré, si oui, se connecter directement
        if (lockfilePath.toFile().exists() && !Utils.readLockFile().isEmpty()) {
            String lockfileContent = Utils.readLockFile();
            if (!lockfileContent.isEmpty()) {
                leagueStarted = true;
                App.getApp().onLeagueStart(lockfileContent);
            } else {
                leagueStarted = false;
            }
        } else {
            leagueStarted = false;
        }

        WatchKey key;
        String lockfileContent;
        try {
            while ((key = watchService.take()) != null) {
                for (WatchEvent<?> event : key.pollEvents()) {
                    // Lockfile modifié -> League mal fermé, on redémarre, ou League qui s'ouvre pour la 1ere fois
                    if (event.kind() == ENTRY_MODIFY && ((Path) event.context()).endsWith("lockfile")) {
                        if (leagueStarted) {
                            continue;
                        }
                        App.getApp().onLeagueStop();
                        lockfileContent = Utils.readLockFile();
                        App.getApp().onLeagueStart(lockfileContent);
                        setLeagueStarted(true);
                    }

                    // Si le lockfile est supprimé, on en déduit que le client est fermé
                    else if (event.kind() == ENTRY_DELETE && ((Path) event.context()).endsWith("lockfile")) {
                        setLeagueStarted(false);
                        App.getApp().onLeagueStop();
                    }
                }
                key.reset();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            Thread.currentThread().interrupt();
        }
    }
}
