package com.joy.downloadtest.downloadtest;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

	private EditText et_path;
	private EditText et_threadCount;
	private LinearLayout ll_pb_layout;
	private String path;
	private static int runningThread;  //����ǰ�������е��߳� 
	private int threadCount;
	private List<ProgressBar> pbLists; //�����������������
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		// [1]�ҵ����ǹ��ĵĿؼ�
		
		et_path = (EditText) findViewById(R.id.et_path);
		et_threadCount = (EditText) findViewById(R.id.et_threadCount);
		ll_pb_layout = (LinearLayout) findViewById(R.id.ll_pb);
		
		
		//[2]���һ������ ����������������� 
		pbLists = new ArrayList<ProgressBar>();
		
	}

	//�����ťʵ�����ص��߼� 
	public void click(View v){
		//1.��ȡ����·��
		path = et_path.getText().toString().trim();

		//2.��ȡ�߳�����
		String threadCountt = et_threadCount.getText().toString().trim();
		//3.���Ƴ������� �����
		ll_pb_layout.removeAllViews();

		threadCount = Integer.parseInt(threadCountt);
		pbLists.clear();
		for(int i = 0;i < threadCount;i++){
			//4.���Ҷ����item����ת����һ��view����
			ProgressBar pbview = (ProgressBar) View.inflate(getApplicationContext(), R.layout.item,null);
			//5.��pbview��ӵ�������
			pbLists.add(pbview);
			//6.��̬��ӽ�����
			ll_pb_layout.addView(pbview);
		}

		//7.��ʼ��ֲ ���� ��ȡ�ļ�����
		new Thread(){
			@Override
			public void run() {
				//��ȥ�������ļ��Ĵ�С
				//Ҫ����ÿ���߳����صĿ�ʼλ�úͽ���λ��
				try{
					//����һ��URL���� ����������ַ
					URL url = new URL(path);
					//��ȡHttpUrlCOnnection ���Ӷ���
					HttpURLConnection conn = (HttpURLConnection) url.openConnection();
					//���ò��� ����get����
					conn.setRequestMethod("GET");
					//�����������糬��ʱ��
					conn.setConnectTimeout(5000);
					int code = conn.getResponseCode();
					if(code == 200){//206�����󲿷���Դ
						int length = conn.getContentLength();
						//���̵߳�������ֵ���������е��߳�
						runningThread = threadCount;

						//����һ����С�ͷ�����һģһ�����ļ�
						//Ŀ����ǰ�ѿռ��������
						RandomAccessFile rafAccessFile = new RandomAccessFile(getFileName(path),"rw");
						rafAccessFile.setLength(length);

						//���ÿ���߳����صĴ�С
						int blockSize = length / threadCount;

						//����ÿ���߳����صĿ�ʼλ�úͽ���λ��
						for(int i = 0;i < threadCount;i++){
							int startIndex = i * blockSize;
							int endIndex = (i + 1) * blockSize - 1;
							if( i == threadCount - 1){
								//˵�������һ���߳�
								endIndex = length - 1;
							}
							//�����߳̽�������
							DownloadThread downloadThread = new DownloadThread(startIndex, endIndex,i);
							downloadThread.start();
						}
					}
				}catch(Exception e){
					e.printStackTrace();
				}
			};}.start();
	}

	private class DownloadThread extends Thread{
		//ͨ�����췽����ÿ���߳����صĿ�ʼλ�úͽ���λ�ô��ݽ���
		private int startIndex;
		private int endIndex;
		private int threadId;

		private int PbMaxSize;//����ǰ�߳����ص����ֵ
		//����жϹ� ��ȡ�ϴ����ص�λ��
		private int pblastPosition;

		public DownloadThread(int startIndex, int endIndex,int threadId){
			this.startIndex = startIndex;
			this.endIndex = endIndex;
			this.threadId = threadId;
		}

		@Override
		public void run() {
			//ʵ��ȥ�����������ļ����߼�
			try{
				//���㵱ǰ�����������ֵ
				PbMaxSize = endIndex - startIndex;
				URL url = new URL(path);
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setRequestMethod("GET");
				conn.setConnectTimeout(5000);

				//����м�Ϲ� �����ϴε�λ��
				//�������� ���ļ��ж�ȡ�ϴ����ص�λ��
				File file = new File(getFileName(path) + threadId+".txt");
				if(file.exists() && file.length() > 0){
					FileInputStream fis = new FileInputStream(file);
					BufferedReader bufr = new BufferedReader(new InputStreamReader(fis));
					String lastPostion = bufr.readLine();
					//��ȡ���������ݾ�����һ�����ص�λ��
					int lastPosition = Integer.parseInt(lastPostion);

					//�����Ƕ���Ľ�����λ�ø�ֵ
					pblastPosition = lastPosition - startIndex;
					//Ҫ�ı�һ�� startIndex λ��
					startIndex = lastPosition + 1;
					fis.close();
				}

				//����һ������ͷ range
				//(���ã����߷�����ÿ���߳����صĿ�ʼλ�úͽ���λ��)
				conn.setRequestProperty("Range","bytes=" + startIndex + "-" + endIndex);

				//��ȡ���������ص�״̬��
				int code = conn.getResponseCode();
				if(code == 206){
					//���������д�ļ�����
					RandomAccessFile raf = new RandomAccessFile(getFileName(path),"rw");
					//ÿ���߳�Ҫ���Լ���λ�ÿ�ʼд
					raf.seek(startIndex);

					InputStream in = conn.getInputStream();

					//������д���ļ���
					int len = -1;
					byte[] buffer = new byte[1024*1024];

					int total = 0;//����ǰ�߳����صĴ�С
					while((len = in.read(buffer)) != -1){
						raf.write(buffer,0,len);
						total += len;

						//ʵ�ֶϵ�����
						int currentThreadPosition = startIndex + total;

						//�����浱ǰ�߳����ص�λ��
						RandomAccessFile raff = new RandomAccessFile(getFileName(path) + threadId + ".txt","rwd");
						raff.write(String.valueOf(currentThreadPosition).getBytes());
						raff.close();

						//����һ�µ�ǰ�����������ֵ �� ��ǰ����
						pbLists.get(threadId).setMax(PbMaxSize);
						pbLists.get(threadId).setProgress(pblastPosition + total);
						//���õ�ǰ�������ĵ�ǰ����
					}
					raf.close();

					//��.txt�ļ�ɾ��
					synchronized (DownloadThread.class){
						runningThread--;
						if(runningThread == 0){
							//˵�����е��̶߳�ִ�������
							for(int i = 0;i < threadCount;i++){
								File delteFile = new File(getFileName(path) + i + ".txt");
								delteFile.delete();
							}
						}
					}
				}
			}catch (Exception e){
				e.printStackTrace();
			}

		}
	}

	//��ȡ�ļ�������  "http://ip:port/XXX.mp3";
	private String getFileName(String path) {
		int start = path.lastIndexOf('/') + 1;
		String subString = path.substring(start);

		String fileName = Environment.getExternalStorageDirectory().getPath()
				+ "/" + subString;
		return fileName;
	}


}
