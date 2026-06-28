package com.example.loyaltyapp.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.loyaltyapp.data.repository.ActivityRepository;
import com.example.loyaltyapp.models.ActivityEvent;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class ActivityViewModel extends ViewModel {

    private final ActivityRepository repository;
    private final MutableLiveData<UserStats> userStats = new MutableLiveData<>();
    private final MutableLiveData<List<ActivityEvent>> displayedEvents = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Date> lastScanTime = new MutableLiveData<>();

    private List<ActivityEvent> allEvents = new ArrayList<>();
    // P1: filter values match the normalized ActivityEvent type enum.
    private String typeFilter = "all"; // all|earn|spend|redemption|bonus
    private Date fromDate = null;
    private Date toDate = null;

    public ActivityViewModel() {
        repository = new ActivityRepository();
    }

    public LiveData<UserStats> getUserStats() { return userStats; }
    public LiveData<List<ActivityEvent>> getDisplayedEvents() { return displayedEvents; }
    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<String> getError() { return error; }
    public LiveData<Date> getLastScanTime() { return lastScanTime; }

    public void loadData() {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) {
            error.setValue("User not logged in");
            return;
        }

        loading.setValue(true);
        repository.getUserStats(uid, new ActivityRepository.OnStatsLoaded() {
            @Override
            public void onSuccess(int points, int visits) {
                userStats.setValue(new UserStats(points, visits));
            }

            @Override
            public void onError(Exception e) {
                error.setValue(e.getMessage());
                loading.setValue(false);
            }
        });

        repository.getActivityHistory(uid, new ActivityRepository.OnActivitiesLoaded() {
            @Override
            public void onSuccess(List<ActivityEvent> events) {
                allEvents = events;
                if (!events.isEmpty() && events.get(0).ts != null) {
                    lastScanTime.setValue(events.get(0).ts.toDate());
                } else {
                    lastScanTime.setValue(null);
                }
                applyFilters();
                loading.setValue(false);
            }

            @Override
            public void onError(Exception e) {
                error.setValue(e.getMessage());
                loading.setValue(false);
            }
        });
    }

    public void setTypeFilter(String type) {
        typeFilter = type;
        applyFilters();
    }

    public void setDateRange(Date from, Date to) {
        fromDate = from;
        toDate = to;
        applyFilters();
    }

    public void resetDateRange() {
        fromDate = null;
        toDate = null;
        applyFilters();
    }

    public void setThisWeekRange() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.set(Calendar.DAY_OF_WEEK, c.getFirstDayOfWeek());
        fromDate = c.getTime();
        c.add(Calendar.DAY_OF_YEAR, 6);
        c.set(Calendar.HOUR_OF_DAY, 23);
        c.set(Calendar.MINUTE, 59);
        c.set(Calendar.SECOND, 59);
        c.set(Calendar.MILLISECOND, 999);
        toDate = c.getTime();
        applyFilters();
    }

    public void setThisMonthRange() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.set(Calendar.DAY_OF_MONTH, 1);
        fromDate = c.getTime();
        c.add(Calendar.MONTH, 1);
        c.add(Calendar.DAY_OF_MONTH, -1);
        c.set(Calendar.HOUR_OF_DAY, 23);
        c.set(Calendar.MINUTE, 59);
        c.set(Calendar.SECOND, 59);
        c.set(Calendar.MILLISECOND, 999);
        toDate = c.getTime();
        applyFilters();
    }

    private void applyFilters() {
        List<ActivityEvent> shownEvents = new ArrayList<>();
        for (ActivityEvent ev : allEvents) {
            if ("all".equals(typeFilter) || typeFilter.equals(ev.type)) {
                if (datePass(ev)) {
                    shownEvents.add(ev);
                }
            }
        }
        displayedEvents.setValue(shownEvents);
    }

    private boolean datePass(ActivityEvent ev) {
        if (fromDate == null && toDate == null) return true;
        Date t = ev.ts != null ? ev.ts.toDate() : null;
        if (t == null) return false;
        if (fromDate != null && t.before(trimStart(fromDate))) return false;
        if (toDate != null && t.after(trimEnd(toDate))) return false;
        return true;
    }

    private static Date trimStart(Date d) {
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    private static Date trimEnd(Date d) {
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        c.set(Calendar.HOUR_OF_DAY, 23);
        c.set(Calendar.MINUTE, 59);
        c.set(Calendar.SECOND, 59);
        c.set(Calendar.MILLISECOND, 999);
        return c.getTime();
    }

    public static class UserStats {
        public final int points;
        public final int visits;
        public UserStats(int points, int visits) {
            this.points = points;
            this.visits = visits;
        }
    }
}
