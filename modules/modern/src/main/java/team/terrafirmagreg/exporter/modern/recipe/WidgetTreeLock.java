package team.terrafirmagreg.exporter.modern.recipe;

import lombok.experimental.UtilityClass;

@UtilityClass
class WidgetTreeLock {
    static final Object LOCK = new Object();
}
