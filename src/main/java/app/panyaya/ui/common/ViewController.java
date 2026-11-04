package app.panyaya.ui.common;

import app.panyaya.AppContext;

/**
 * Every screen controller implements this. {@link #init} is called once after the FXML is loaded;
 * {@link #refresh} every time the screen is shown or the data changed.
 */
public interface ViewController {

    void init(AppContext context, Shell shell);

    void refresh();
}
