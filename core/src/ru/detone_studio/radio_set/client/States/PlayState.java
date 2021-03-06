package ru.detone_studio.radio_set.client.States;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.audio.AudioDevice;
import com.badlogic.gdx.audio.AudioRecorder;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.net.Socket;
import com.badlogic.gdx.net.SocketHints;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import java.nio.ByteBuffer;
import java.util.Arrays;

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

    Texture btn_green_tex=new Texture(Gdx.files.internal("btn_green.png"));
    Texture btn_red_tex=new Texture(Gdx.files.internal("btn_red.png"));

    Sprite btn_green ;
    Sprite btn_red;

    Sprite crl_green = new Sprite(new Texture(Gdx.files.internal("circle_green.png")));
    Sprite crl_yellow = new Sprite(new Texture(Gdx.files.internal("circle_yellow.png")));
    Sprite crl_red = new Sprite(new Texture(Gdx.files.internal("circle_red.png")));
    Sprite crl_blue = new Sprite(new Texture(Gdx.files.internal("circle_blue.png")));

    //Буффер рукопожатия
    byte hand_shake_buffer[] = new byte[2];

    //Буффер отправки
    int size_of_system_buffer=4096*2;
    byte system_buffer[]=new byte[size_of_system_buffer];

    //Порт авторизации и ИП
    int dynamic_port = 9000;
    String ip_adress = "192.168.0.2";


    String about = "";
    //String ip_adress="185.132.242.124";
    static boolean btn_touched = false;


    public PlayState(GameStateManager gsm) {
        super(gsm);
        camera = new OrthographicCamera();
        camera.setToOrtho(false, 480, 800);

        //Кнопки
        btn_red_tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        btn_green_tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        btn_green=new Sprite(btn_green_tex);
        btn_red=new Sprite(btn_red_tex);
        btn_green.setPosition(167, 167);
        btn_red.setPosition(167, 167);

        btn_green.scale(2.0f);
        btn_red.scale(2.0f);

        crl_green.setPosition(406, 726);
        crl_red.setPosition(406, 726);
        crl_blue.setPosition(406, 726);
        crl_yellow.setPosition(406, 726);

        Input.TextInputListener listener = new Input.TextInputListener() {
            @Override
            public void input(String text) {
                ip_adress = text;
                //поток обмена данными
                send_msg();
            }

            @Override
            public void canceled() {
                //поток обмена данными
                send_msg();
            }
        };
        about = "Wait for input ServerAdress";


        Gdx.input.getTextInput(listener, "Enter server adress", "185.132.242.124", "");

        //поток сохранения шумов(для тестов)
        //save_noice();

        //поток обмена данными
        //send_msg();


        //поток воспроизведения звука
        play_snd();

        //поток сохранения звука
        save_snd();


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
        sync_dt += dt;
        if (current_dt > 1.0f) {
            btn_touched = false;
            handleInput();
        }
    }

    @Override
    public void render(SpriteBatch sb) {
        sb.setProjectionMatrix(camera.combined);
        if (!btn_touched) {
            // font.setColor(1.0f, 0.0f, 0.0f, 0.5f);
            btn_red.draw(sb);

        } else {
            // font.setColor(0.0f, 1.0f, 0.0f, 1.0f);
            btn_green.draw(sb);
        }
        // int kk=hand_shake_buffer[0];
        //font.draw(sb, " BUFFER  : " , 10, 770);
        if (hand_shake_buffer[0] == 25) {
            crl_blue.draw(sb);
        } else if (hand_shake_buffer[0] == 20) {
            crl_green.draw(sb);
        } else if (hand_shake_buffer[0] == 21) {
            crl_yellow.draw(sb);
        } else {
            crl_red.draw(sb);
        }
        font.draw(sb, about, 15, 770);
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
        font.draw(sb, " FPS : " + fps, 10, 790);
        font.setColor(1, 1, 1, 1);


    }

    @Override
    public void dispose() {

    }


    public void send_msg() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("Start send/recv Tread");
                SocketHints hints = new SocketHints();

                hints.socketTimeout = 5000;
                //hints.trafficClass = 0x08;
                //hints.receiveBufferSize=2048;
                //hints.sendBufferSize=2048;
                //byte hand_shake_buffer[]=new byte[2];
                byte buffer[] = new byte[196];
                while (true) {
                    try {
                        //Важная строка для синхронизации
                        System.out.print("");

                        if (sync_dt >= 0.8f) {
                            Socket client;
                            if (dynamic_port == 9000) {
                                hand_shake_buffer[0] = 21;
                                about = "Try to connect: " + ip_adress + ":" + dynamic_port;
                                Thread.sleep(2000);
                                client = Gdx.net.newClientSocket(Net.Protocol.TCP, ip_adress, 9000, hints);
                            } else {
                                about = "Check aviable: " + ip_adress + ":" + dynamic_port;

                                //int buffer_size = 4096 ;
                                int buffer_size = size_of_system_buffer;
                                hints.receiveBufferSize = buffer_size;
                                hints.sendBufferSize = buffer_size;

                                client = Gdx.net.newClientSocket(Net.Protocol.TCP, ip_adress, dynamic_port, hints);
                            }


                            //Отправка на сервер------------------------------------------------------------------>
                            if (can_send.size >= 1) {
                                if (can_send.get(0)) {
                                    //handShake
                                    about = "Send data to: " + ip_adress + ":" + dynamic_port;
                                    hand_shake_buffer[0] = 15;
                                    //Send 15 have sound
                                    client.getOutputStream().write(hand_shake_buffer);
                                    client.getOutputStream().flush();

                                    //read answer 20
                                    client.getInputStream().read(hand_shake_buffer);

                                    ByteBuffer buffer2 = ByteBuffer.allocate(2);
                                    sync_j = 0;

                                    /*
                                    for (int i = 1; i <= 225; i++) {
                                        for (int j = 1; j <= 98; j++) {
                                            buffer2.putShort(data.get(0)[sync_j]);
                                            buffer[j * 2 - 2] = buffer2.get(0);
                                            buffer[j * 2 - 1] = buffer2.get(1);
                                            buffer2.clear();
                                            sync_j++;
                                        }
                                        client.getOutputStream().write(buffer);
                                    }
                                    */

                                    byte null_byte=0;
                                    boolean send_wrong_block=true;
                                    int sended_blocks=0;
                                    for (int i=0;i<samples;i++){

                                        //buffer2.clear();
                                        buffer2.putShort(data.get(0)[i]);
                                        //System.out.println("N: "+i+" s: "+data.get(0)[i]);

                                        if (sync_j<size_of_system_buffer/2) {
                                        system_buffer[sync_j*2+0]=buffer2.get(0);
                                        system_buffer[sync_j*2+1]=buffer2.get(1);
                                        buffer2.clear();
                                            sync_j++;
                                            if (sync_j>=size_of_system_buffer/2){
                                                sended_blocks++;
                                                System.out.println("Sended: "+sended_blocks);
                                                client.getOutputStream().write(system_buffer);

                                                System.out.println("Wait answer: "+sended_blocks);
                                                client.getInputStream().read(hand_shake_buffer);
                                                sync_j=0;
                                            }
                                        }
                                     //   else
                                      //  {

                                       //     sended_blocks++;
                                         //   system_buffer[0]=(byte) sended_blocks;
                                         //   system_buffer[1]=(byte) 12;

                                         //   client.getOutputStream().write(system_buffer);


                                         //   client.getInputStream().read(hand_shake_buffer);


                                          //  client.getOutputStream().flush();
                                          //  Thread.sleep(120);

                                         //   System.out.println("Sended: "+sync_j+" bm1: "+system_buffer[0]+" bm2: "+system_buffer[1]+"ans: "+hand_shake_buffer[0]);
                                          //  sync_j=1;

                                          //  Arrays.fill(system_buffer,null_byte);
                                        //}

                                    }

                                    System.out.println("Sended: "+sended_blocks);
                                    client.getOutputStream().write(system_buffer);

                                    System.out.println("Wait answer: "+sended_blocks);
                                    client.getInputStream().read(hand_shake_buffer);
                                    System.out.println("answer: "+hand_shake_buffer[0]);
                                    //Arrays.fill(system_buffer,null_byte);

                                    data.removeIndex(0);
                                    can_send.removeIndex(0);
                                    System.out.println("END SEND");
                                }
                            }
                            //------------------------------------------------------------------------------------->
                            else {
                                if (dynamic_port == 9000) {

                                    hand_shake_buffer[0] = 11;
                                    client.getOutputStream().write(hand_shake_buffer);
                                    client.getInputStream().read(hand_shake_buffer);
                                    System.out.println("Answer: " + hand_shake_buffer[0]);

                                    byte port_buffer[] = new byte[2];
                                    client.getInputStream().read(port_buffer);

                                    System.out.println("port to connect: " + port_buffer[0]);
                                    client.dispose();
                                    dynamic_port = port_buffer[0];
                                    dynamic_port += 9000;

                                    System.out.println("Conncet to : " + dynamic_port);
                                    client = Gdx.net.newClientSocket(Net.Protocol.TCP, ip_adress, dynamic_port, hints);
                                }

                                hand_shake_buffer[0] = 10;

                                client.getOutputStream().write(hand_shake_buffer);
                                System.out.println("Port: " + dynamic_port);

                                client.getInputStream().read(hand_shake_buffer);
                                System.out.println("Nothing hand_shake_buffer[0]: " + hand_shake_buffer[0]);

                                //Получение данных с сервера-------------------------------------------------------->
                                if (hand_shake_buffer[0] == 25) {
                                    //Статус сообщение
                                    about = "Receive data from: " + ip_adress + ":" + dynamic_port;


                                    sync_i = 0;
                                    data_rcv.add(new short[samples]);
                                    ByteBuffer buffer2 = ByteBuffer.allocate(2);

                                    for (local_i = 1; local_i <= 225; local_i++) {
                                        client.getInputStream().read(buffer);
                                        for (int j = 1; j <= 98; j++) {

                                            buffer2.put(buffer[j * 2 - 2]);
                                            buffer2.put(buffer[j * 2 - 1]);
                                            data_rcv.get(data_rcv.size - 1)[sync_i] = buffer2.getShort(0);
                                            buffer2.clear();

                                            sync_i++;
                                        }
                                        //buffer2.putShort(data.get(0)[i]);
                                    }

                                    System.out.println("ublocked");
                                    blocked.add(false);


                                }
                                //---------------------------------------------------------------------------------->


                            }

                            sync_dt = 0;
                            client.dispose();
                        }
                        //wait(900);

                    } catch (GdxRuntimeException err) {
                        String error = err.getMessage();
                        if (error.contains("Error making a socket connection")) {
                            //System.out.println(error);
                            try {
                                about = "Server offline or Blocked ip: " + ip_adress + ":" + dynamic_port;
                                hand_shake_buffer[0] = 0;
                                Thread.sleep(4000);

                            } catch (Exception ignore) {

                            }
                        }
                        //Gdx.app.log("PingPongSocketExample", "Connect Exception", err);

                    } catch (Exception e) {

                        hand_shake_buffer[0] = 0;
                        Gdx.app.log("PingPongSocketExample", "Error Transmit", e);
                        try {
                            about = "Connection error: " + ip_adress + ":" + dynamic_port;
                            Thread.sleep(2000);

                        } catch (Exception ignore) {

                        }
                    }

                }
            }
        }).start();

    }


    public void play_snd() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        if (blocked != null) {
                            if (blocked.size > 0) {
                                if (!blocked.get(0)) {
                                    blocked.set(0, true);
                                    player.writeSamples(data_rcv.get(0), 0, 22050);
                                    data_rcv.removeIndex(0);
                                    blocked.removeIndex(0);
                                } else {
                                    System.out.println("Data blocked");
                                }
                            }

                        }
                    } catch (Exception e) {
                        Gdx.app.log("PingPongSocketExample", "an error occured", e);
                    }
                    System.out.print("");
                }

            }
        }).start();

    }

    public void save_snd() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    if (touched) {
                        touched = false;
                        can_send.add(false);
                        data.add(new short[samples * 1]);
                        recorder.read(data.get(data.size - 1), 0, data.get(data.size - 1).length);
                        can_send.set(can_send.size - 1, true);
                        // blocked.set(blocked.size - 1, false);
                        //send_msg();
                    }
                }
            }
        }).start();
    }

    //Функция генерации шумов
    public void save_noice() {
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
                            System.out.println("End Generating Noice");
                        }


                    }
                }
            }).start();
        } catch (Exception e) {
            Gdx.app.log("Radio_set_client", "Noice fuction Error", e);
        }
    }


}