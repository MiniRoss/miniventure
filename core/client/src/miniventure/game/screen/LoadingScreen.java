package miniventure.game.screen;

import java.util.Stack;

import miniventure.game.util.ProgressLogger;

import com.badlogic.gdx.Gdx;
import com.kotcrab.vis.ui.widget.VisLabel;

public class LoadingScreen extends MenuScreen implements ProgressLogger {
	
	/*
		I want to have a system where it displays a message, and the message shows a #/total progress format.
		Also, you should be able to have multiple of these, so I can say:
		
		Loading level 1/5...
		
		Loading entity 4/217...
		
		Like that.  
	 */
	
	private Stack<VisLabel> messageLabels = new Stack<>();
	
	public LoadingScreen() {
		addActor(vGroup);
	}
	
	@Override
	public void pushMessage(String message) {
		final VisLabel label = new VisLabel(message);
		messageLabels.push(label);
		Gdx.app.postRunnable(() -> {
			vGroup.addActor(label);
		});
	}
	
	@Override
	public void editMessage(final String message) {
		final VisLabel label = messageLabels.peek();
		Gdx.app.postRunnable(() -> label.setText(message));
	}
	
	@Override
	public void popMessage() {
		VisLabel removed = messageLabels.pop();
		Gdx.app.postRunnable(() -> vGroup.removeActor(removed));
	}
	
}
