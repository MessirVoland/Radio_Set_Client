package ru.detone_studio.radio_set.client;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

import ru.detone_studio.radio_set.client.States.PlayState;

public class MainClass extends ApplicationAdapter {
	SpriteBatch batch;
	private GameStateManager gsm;
	//Texture img;
	
	@Override
	public void create () {
		batch = new SpriteBatch();
		//img = new Texture("egg.png");
		//img.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
		gsm = new GameStateManager();
		gsm.push(new PlayState(gsm));
	}

	@Override
	public void render () {
		Gdx.gl.glClearColor(0, 0, 0, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

		//batch.draw(img, 0, 0);

		gsm.update(Gdx.graphics.getDeltaTime());
		batch.begin();
		gsm.render(batch);
		batch.end();
	}
	
	@Override
	public void dispose () {
		batch.dispose();
		//img.dispose();
	}
}
