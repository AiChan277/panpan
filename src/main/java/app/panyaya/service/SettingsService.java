package app.panyaya.service;

import app.panyaya.exception.BusinessException;
import app.panyaya.exception.PersistenceException;
import app.panyaya.model.BakerySettings;
import app.panyaya.model.user.Permission;
import app.panyaya.persistence.DataStore;

import java.time.Clock;

/** Bakery profile and thresholds. */
public class SettingsService extends BaseService {

    public SettingsService(DataStore store, Session session, Clock clock) {
        super(store, session, clock);
    }

    public BakerySettings getSettings() {
        return data().getSettings();
    }

    public void updateProfile(String name, String address, String phone, String footer)
            throws BusinessException, PersistenceException {
        session.require(Permission.MANAGE_SETTINGS);
        store.execute("UPDATE_SETTINGS", data -> {
            data.getSettings().updateProfile(name, address, phone, footer);
            audit(data, "UPDATE_SETTINGS", "bakery profile");
            return null;
        });
    }

    public void updateThresholds(int nearExpiryDays, int nearExpiryHours, int backupsToKeep, String timeZone)
            throws BusinessException, PersistenceException {
        session.require(Permission.MANAGE_SETTINGS);
        store.execute("UPDATE_SETTINGS", data -> {
            data.getSettings().updateThresholds(nearExpiryDays, nearExpiryHours, backupsToKeep, timeZone);
            audit(data, "UPDATE_SETTINGS", "thresholds");
            return null;
        });
    }
}
