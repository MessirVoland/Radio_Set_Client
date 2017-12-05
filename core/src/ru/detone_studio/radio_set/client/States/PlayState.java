package ru.detone_studio.radio_set.client.States;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.audio.AudioDevice;
import com.badlogic.gdx.audio.AudioRecorder;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.net.Socket;
import com.badlogic.gdx.net.SocketHints;
import com.badlogic.gdx.utils.Array;

import java.nio.ByteBuffer;

import ru.detone_studio.radio_set.client.GameStateManager;

import static com.badlogic.gdx.math.MathUtils.random;

/**
 * Основной модуль клиента
 * Created by Voland on 29.10.2017.
 */

public class PlayState extends State {
    //хз что это
    Texture img;
    //камера
    private OrthographicCamera camera;

    static final int samples = 22050; //размер дискретки звука
    static boolean isMono = true; //моно или стерео
    static final Array<short[]> data = new Array<short[]>(); // массив данных для отправки
    static final Array<short[]> data_rcv = new Array<short[]>(); // массив данны для приема
    static final Array<Boolean> blocked = new Array<Boolean>(); // список блокировок передачи
    static final Array<Boolean> can_send = new Array<Boolean>(); //список блокировок записи

    private BitmapFont font = new BitmapFont();

    //Исп для отслеживания рамзерности массивов
    int local_i;
    int sync_i, sync_j;
    //short[] data = new short[samples*1];


    //запись и воспроизведение
    static final AudioRecorder recorder = Gdx.audio.newAudioRecorder(samples, isMono);
    static final AudioDevice player = Gdx.audio.newAudioDevice(22050, isMono);


    static boolean touched = false;
    float current_dt = 0.0f;
    float sync_dt = 0.0f;

    Sprite btn_green=new Sprite(new Texture(Gdx.files.internal("btn_green.png")));
    Sprite btn_red=new Sprite(new Texture(Gdx.files.internal("btn_red.png")));

    Sprite crl_green=new Sprite(new Texture(Gdx.files.internal("circle_green.png")));
    Sprite crl_yellow=new Sprite(new Texture(Gdx.files.internal("circle_yellow.png")));
    Sprite crl_red=new Sprite(new Texture(Gdx.files.internal("circle_red.png")));
    Sprite crl_blue=new Sprite(new Texture(Gdx.files.internal("circle_blue.png")));

    //Буффер рукопожатия
    byte hand_shake_buffer[]=new byte[2];
    //Порт авторизации и ИП
    int dynamic_port=9000;
    String ip_adress="192.168.0.2";
    //String ip_adress="185.132.242.124";
    static boolean btn_touched=false;

    public PlayState(GameStateManager gsm) {
        super(gsm);
        camera = new OrthographicCamera();
        camera.setToOrtho(false, 480, 800);

        //Кнопки
        btn_green.setPosition(167,167);
        btn_red.setPosition(167,167);

        crl_green.setPosition(406,726);
        crl_red.setPosition(406,726);
        crl_blue.setPosition(406,726);
        crl_yellow.setPosition(406,726);
        //поток сохранения шумов(для тестов)
        save_noice();

        //поток обмена данными
        send_msg();



        //поток воспроизведения звука
        play_snd();

        //поток сохранения звука
        //save_snd();



    }

    @Override
    protected void handleInput() {
        Vector3 touchPos = new Vector3();
        touchPos.set(Gdx.input.getX(), Gdx.input.getY(), 0);
        camera.unproject(touchPos);
        if (Gdx.input.isTouched()) {
            if ((btn_green.getBoundingRectangle().contains(touchPos.x, touchPos.y)) | (btn_red.getBoundingRectangle().contains(touchPos.x, touchPos.y))) {
                touched = true;
                btn_touched = true;
                current_dt = 0;
            }
        }


    }

    @Override
    public void update(float dt) {
        current_dt += dt;
        sync_dt+=dt;
        if (current_dt > 1.0f) {
            btn_touched=false;
            handleInput();
        }
    }

    @Override
    public void render(SpriteBatch sb) {
        sb.setProjectionMatrix(camera.combined);
        if (!btn_touched) {
            // font.setColor(1.0f, 0.0f, 0.0f, 0.5f);
            btn_red.draw(sb);

        }
        else
        {
            // font.setColor(0.0f, 1.0f, 0.0f, 1.0f);
            btn_green.draw(sb);
        }
        // int kk=hand_shake_buffer[0];
        //font.draw(sb, " BUFFER  : " , 10, 770);
        if (hand_shake_buffer[0]==25){
            crl_blue.draw(sb);
        }else if (hand_shake_buffer[0]==20){
            crl_green.draw(sb);
        }else if (hand_shake_buffer[0]==21){
            crl_yellow.draw(sb);
        }
        else{
            crl_red.draw(sb);
        }
        font.draw(sb,"Text",15,770);
        //Справочная информация

        int fps = Gdx.graphics.getFramesPerSecond();
        if (fps >= 45) {
            // 45 or more FPS show up in green
            font.setColor(0, 1, 0, 1);
        } else if (fps >= 30) {
            // 30 or more FPS show up in yellow
            font.setColor(1, 1, 0, 1);
        } else {
            // less than 30 FPS show up in red
            font.setColor(1, 0, 0, 1);
        }
        font.draw(sb, " FPS : "+  fps, 10, 790);
        font.setColor(1, 1, 1, 1);


    }

    @Override
    public void dispose() {

    }



    public void send_msg(){
        new Thread(new Runnable() {
            @Override
            public void run () {
                System.out.println("Start send/recv Tread");
                SocketHints hints = new SocketHints();

                hints.socketTimeout = 5000;
                hints.trafficClass=0x08;
                //hints.receiveBufferSize=2048;
                //hints.sendBufferSize=2048;
                //byte hand_shake_buffer[]=new byte[2];
                byte buffer[] = new byte[392];
                while (true) {
                    try {
                        //Важная строка для синхронизации
                        System.out.print("");

                        if (sync_dt>=0.8f) {
                            Socket client;
                            if (dynamic_port==9000){
                                hand_shake_buffer[0]=21;
                                Thread.sleep(2000);
                                client = Gdx.net.newClientSocket(Net.Protocol.TCP, ip_adress, 9000, hints);
                            }else
                            {
                                client = Gdx.net.newClientSocket(Net.Protocol.TCP, ip_adress, dynamic_port, hints);
                            }


                            if (can_send.size >= 1) {
                                if (can_send.get(0)) {
                                    //handShake
                                    hand_shake_buffer[0] = 15;
                                    client.getOutputStream().write(hand_shake_buffer);

                                    client.getInputStream().read(hand_shake_buffer);


                                    ByteBuffer buffer2 = ByteBuffer.allocate(2);

                                    sync_j = 0;
                                    for (int i = 1; i <= (225); i++) {
                                        for (int j = 1; j < (99); j++) {
                                            buffer2.putShort(data.get(0)[sync_j]);
                                            buffer[j * 2 - 2] = buffer2.get(0);
                                            buffer[j * 2 - 1] = buffer2.get(1);
                                            buffer2.clear();
                                            sync_j++;
                                        }
                                        client.getOutputStream().write(buffer);
                                    }
                                    data.removeIndex(0);
                                    can_send.removeIndex(0);
                                }
                            }
                            else {
                                if (dynamic_port==9000) {

                                    hand_shake_buffer[0] = 11;
                                    client.getOutputStream().write(hand_shake_buffer);
                                    client.getInputStream().read(hand_shake_buffer);
                                    System.out.println("Answer: " + hand_shake_buffer[0]);

                                    byte port_buffer[]=new byte[2];
                                    client.getInputStream().read(port_buffer);

                                    System.out.println("port to connect: " + port_buffer[0]);
                                    client.dispose();
                                    dynamic_port=port_buffer[0];
                                    dynamic_port+=9000;

                                    System.out.println("Conncet to : "+dynamic_port);
                                    client = Gdx.net.newClientSocket(Net.Protocol.TCP, ip_adress, dynamic_port, hints);
                                }

                                hand_shake_buffer[0] = 10;

                                client.getOutputStream().write(hand_shake_buffer);
                                System.out.println("Port: "+dynamic_port);

                                client.getInputStream().read(hand_shake_buffer);
                                System.out.println("Nothing hand_shake_buffer[0]: "+hand_shake_buffer[0]);
                                if (hand_shake_buffer[0]==25){

                                    sync_i = 0;
                                    data_rcv.add(new short[samples * 1]);
                                    for (local_i = 1; local_i <= (225); local_i++) {
                                        client.getInputStream().read(buffer);
                                        //System.out.println("i "+local_i);
                                        //buffer2.order(ByteOrder.LITTLE_ENDIAN);
                                        for (int j = 1; j < (99); j++) {
                                            //byte buffer[] = new byte[392];
                                            //System.out.println("buffer");
                                            ByteBuffer buffer2 = ByteBuffer.allocate(2);
                                            //System.out.println("buffer2");

                                            System.out.println("data_rcv");

                                            buffer2.put(buffer[j * 2 - 2]);
                                            buffer2.put(buffer[j * 2 - 1]);
                                            // System.out.println("buffer2 in "+buffer2);
                                            data_rcv.get(data_rcv.size - 1)[sync_i] = buffer2.getShort(0);
                                            buffer2.clear();

                                            if (local_i * j >= (22049)) {
                                                System.out.println("ublocked");
                                                //System.out.println("buffer.rcv 0 " + data_rcv.get(0)[0]);
                                                //System.out.println("buffer.rcv 1 " + data_rcv.get(0)[1]);
                                                //System.out.println("buffer.rcv 2 " + data_rcv.get(0)[2]);
                                                //System.out.println("buffer.rcv 3 " + data_rcv.get(0)[3]);
                                                //System.out.println("buffer.rcv 4 " + data_rcv.get(0)[4]);
                                                blocked.add(false);
                                            }
                                            sync_i++;
                                        }
                                        //buffer2.putShort(data.get(0)[i]);
                                    }

                                }

                            }

                            sync_dt=0;
                            client.dispose();
                        }
                        //wait(900);

                    }
                    catch (Exception e)
                    {

                        hand_shake_buffer[0]=0;
                        Gdx.app.log("PingPongSocketExample", "Error Transmit", e);
                        try{
                            Thread.sleep(2000);
                        }catch (Exception ignore){

                        }
                    }

                }
            }
        }).start();

    }


    public void play_snd(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        if (blocked != null) {
                            if (blocked.size > 0) {
                                if (!blocked.get(0)) {
                                    blocked.set(0,true);
                                    player.writeSamples(data_rcv.get(0), 0, 22050);
                                    data_rcv.removeIndex(0);
                                    blocked.removeIndex(0);
                                } else
                                {
                                    System.out.println("Data blocked");
                                }
                            }

                        }
                    }catch (Exception e){
                        Gdx.app.log("PingPongSocketExample", "an error occured", e);
                    }
                    System.out.print("");
                }

            }
        }).start();

    }

    public void save_snd(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    if (touched) {
                        touched = false;
                        can_send.add(false);
                        data.add(new short[samples * 1]);
                        recorder.read(data.get(data.size - 1), 0, data.get(data.size - 1).length);
                        can_send.set(can_send.size-1,true);
                        // blocked.set(blocked.size - 1, false);
                        //send_msg();
                    }
                }
            }
        }).start();
    }

    //Функция генерации шумов
    public void save_noice(){
        System.out.println("Start noice fuction");
        try {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    System.out.println("Start noice Runnable");
                    while (true) {

                        //Важная строка для синхронизации
                        System.out.print("");

                        if (touched) {
                            touched = false;
                            can_send.add(false);
                            System.out.println("Start Generating Noice");
                            data.add(new short[samples * 1]);
                            for (int i = 1; i < 22050; i++) {
                                data.get(data.size - 1)[i] = (short) (random.nextInt(64000) - 32000);
                                //if (i>=44099){
                                //   System.out.println("unblock");
                                //    blocked.add(false);
                                // }
                            }
                            can_send.set(can_send.size - 1, true);
                        }


                    }
                }
            }).start();
        }catch (Exception e){
            Gdx.app.log("Radio_set_client", "Noice fuction Error", e);
        }
    }


}