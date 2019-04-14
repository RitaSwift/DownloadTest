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
	private static int runningThread;  //代表当前正在运行的线程 
	private int threadCount;
	private List<ProgressBar> pbLists; //用来存进度条的引用
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		// [1]找到我们关心的控件
		
		et_path = (EditText) findViewById(R.id.et_path);
		et_threadCount = (EditText) findViewById(R.id.et_threadCount);
		ll_pb_layout = (LinearLayout) findViewById(R.id.ll_pb);
		
		
		//[2]添加一个集合 用来存进度条的引用 
		pbLists = new ArrayList<ProgressBar>();
		
	}

	//点击按钮实现下载的逻辑 
	public void click(View v){
		//1.获取下载路径
		path = et_path.getText().toString().trim();

		//2.获取线程数量
		String threadCountt = et_threadCount.getText().toString().trim();
		//3.先移除进度条 再添加
		ll_pb_layout.removeAllViews();

		threadCount = Integer.parseInt(threadCountt);
		pbLists.clear();
		for(int i = 0;i < threadCount;i++){
			//4.把我定义的item布局转换成一个view对象
			ProgressBar pbview = (ProgressBar) View.inflate(getApplicationContext(), R.layout.item,null);
			//5.把pbview添加到集合中
			pbLists.add(pbview);
			//6.动态添加进度条
			ll_pb_layout.addView(pbview);
		}

		//7.开始移植 联网 获取文件长度
		new Thread(){
			@Override
			public void run() {
				//获去服务器文件的大小
				//要计算每个线程下载的开始位置和结束位置
				try{
					//创建一个URL对象 参数就是网址
					URL url = new URL(path);
					//获取HttpUrlCOnnection 链接对象
					HttpURLConnection conn = (HttpURLConnection) url.openConnection();
					//设置参数 发送get请求
					conn.setRequestMethod("GET");
					//设置连接网络超市时间
					conn.setConnectTimeout(5000);
					int code = conn.getResponseCode();
					if(code == 200){//206是请求部分资源
						int length = conn.getContentLength();
						//把线程的数量赋值给正在运行的线程
						runningThread = threadCount;

						//创建一个大小和服务器一模一样的文件
						//目的提前把空间申请出来
						RandomAccessFile rafAccessFile = new RandomAccessFile(getFileName(path),"rw");
						rafAccessFile.setLength(length);

						//算出每个线程下载的大小
						int blockSize = length / threadCount;

						//计算每个线程下载的开始位置和结束位置
						for(int i = 0;i < threadCount;i++){
							int startIndex = i * blockSize;
							int endIndex = (i + 1) * blockSize - 1;
							if( i == threadCount - 1){
								//说明是最后一个线程
								endIndex = length - 1;
							}
							//几个线程进行下载
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
		//通过构造方法把每个线程下载的开始位置和结束位置传递进来
		private int startIndex;
		private int endIndex;
		private int threadId;

		private int PbMaxSize;//代表当前线程下载的最大值
		//如果中断过 获取上次下载的位置
		private int pblastPosition;

		public DownloadThread(int startIndex, int endIndex,int threadId){
			this.startIndex = startIndex;
			this.endIndex = endIndex;
			this.threadId = threadId;
		}

		@Override
		public void run() {
			//实现去服务器下载文件的逻辑
			try{
				//计算当前进度条的最大值
				PbMaxSize = endIndex - startIndex;
				URL url = new URL(path);
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setRequestMethod("GET");
				conn.setConnectTimeout(5000);

				//如果中间断过 继续上次的位置
				//继续下载 从文件中读取上次下载的位置
				File file = new File(getFileName(path) + threadId+".txt");
				if(file.exists() && file.length() > 0){
					FileInputStream fis = new FileInputStream(file);
					BufferedReader bufr = new BufferedReader(new InputStreamReader(fis));
					String lastPostion = bufr.readLine();
					//读取出来的内容就是上一次下载的位置
					int lastPosition = Integer.parseInt(lastPostion);

					//给我们定义的进度条位置赋值
					pblastPosition = lastPosition - startIndex;
					//要改变一下 startIndex 位置
					startIndex = lastPosition + 1;
					fis.close();
				}

				//设置一个请求头 range
				//(作用：告诉服务器每个线程下载的开始位置和结束位置)
				conn.setRequestProperty("Range","bytes=" + startIndex + "-" + endIndex);

				//获取服务器返回的状态嘛
				int code = conn.getResponseCode();
				if(code == 206){
					//创建随机读写文件对象
					RandomAccessFile raf = new RandomAccessFile(getFileName(path),"rw");
					//每个线程要从自己的位置开始写
					raf.seek(startIndex);

					InputStream in = conn.getInputStream();

					//把数据写到文件中
					int len = -1;
					byte[] buffer = new byte[1024*1024];

					int total = 0;//代表当前线程下载的大小
					while((len = in.read(buffer)) != -1){
						raf.write(buffer,0,len);
						total += len;

						//实现断点续传
						int currentThreadPosition = startIndex + total;

						//用来存当前线程下载的位置
						RandomAccessFile raff = new RandomAccessFile(getFileName(path) + threadId + ".txt","rwd");
						raff.write(String.valueOf(currentThreadPosition).getBytes());
						raff.close();

						//设置一下当前进度条的最大值 和 当前进度
						pbLists.get(threadId).setMax(PbMaxSize);
						pbLists.get(threadId).setProgress(pblastPosition + total);
						//设置当前进度条的当前进度
					}
					raf.close();

					//把.txt文件删除
					synchronized (DownloadThread.class){
						runningThread--;
						if(runningThread == 0){
							//说明所有的线程都执行完毕了
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

	//获取文件的名字  "http://ip:port/XXX.mp3";
	private String getFileName(String path) {
		int start = path.lastIndexOf('/') + 1;
		String subString = path.substring(start);

		String fileName = Environment.getExternalStorageDirectory().getPath()
				+ "/" + subString;
		return fileName;
	}


}
