package com.example.myapplication;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;

/*
 * AscynTask本身就是子线程执行后台任务，并提交结果到UI线程的一个任务类，
 * 子线程会返回后台任务执行后的结果，可以使用Callable和future对象
 * 将返回结果更新到UI线程可以使用hander
 * 在执行任务或请求时，他所需要的参数和返回值等都是不定的所以用泛型定义
 * */
public abstract class MyAsyncTask<Params, Progress, Result> {

    private WorkerRunnable<Params, Result> mWorker;

    /*
    *FutureTask在AsyncTask里充当了线程的角色，
    *因此耗时的后台任务doInBackground应该在FutureTask中调用，
    *同时我们还要提供线程池对象来执行FutureTask
    * FutureTask既是Callable也是Future对象
    */
    private static final Executor EXECUTOR = Executors.newCachedThreadPool();
    private FutureTask<Result> mFuture;

    private static final int MESSAGE_POST_RESULT = 0x1;
    private static final int MESSAGE_POST_PROGRESS = 0x2;

    /**
     * AsyncTask会在各Activity中实例化，
     * 有可能在主线程或子线程中实例化，这么多的AsyncTask实例中，
     * 我们只需要一个持有mainLooper的Handler的实例，
     * 因此它将是一个单例对象，单个进程内共享。
     */
    private static InternalHandler sHandler = new InternalHandler();

    /**
     * 是否取消
     */
    private final AtomicBoolean mCancelled = new AtomicBoolean();

    /*
    * 这是MyAsyncTask的一个构造器
    * 会创建一个mworker储存所要执行的任务
    * 任务在运行时就会调用doInBackground和postResult
    * 在子线程拉取数据，再将结果返回给hander来更新UI
    * */
    public MyAsyncTask() {
//        WorkerRunnable是已经实现Callable接口的声明请求任务时的参数的类
//        作为每一个要执行的具体的任务或请求
        mWorker = new WorkerRunnable<Params, Result>() {
            @Override
            public Result call() throws Exception {
                //调用模板方法2-执行后台任务
                Result result = doInBackground(mParams);
                //提交结果给Handler
                return postResult(result);
            }
        };

        //此为线程对象
        //将具体的任务传到线程
        mFuture = new FutureTask<>(mWorker);
    }

    public void execute(Params params) {
        mWorker.mParams = params;
        //在线程启动前调用预执行的模板方法，意味着它在调用AsyncTask.execute()的所在线程里执行，如果是在子线程中，则无法处理UI
        //调用模板方法1-预执行
        onPreExecute();
        //执行FutureTask启动线程
        EXECUTOR.execute(mFuture);
    }
    //触发onProgressUpdate
    protected final void publishProgress(Progress progress) {
        if (!isCancelled()) {
            AsyncTaskResult<Progress> taskResult = new AsyncTaskResult<>(this, progress);
            sHandler.obtainMessage(MESSAGE_POST_PROGRESS, taskResult).sendToTarget();
        }
    }
    
    private Result postResult(Result result) {
        AsyncTaskResult<Result> taskResult = new AsyncTaskResult<>(this, result);
        Message message = sHandler.obtainMessage(MESSAGE_POST_RESULT, taskResult);
        message.sendToTarget();
        return result;
    }


    /**
     * 取消任务
     * 此方法不让重写，因此定义为final方法
     */
    public final boolean cancel(boolean mayInterruptIfRunning) {
        mCancelled.set(true);
        return mFuture.cancel(mayInterruptIfRunning);
    }
    /*
    * 终止线程或者线程结束将这两个方法包装
    * */
    private void finish(Result result) {
        if (isCancelled()) {
            //调用模板方法5：终止线程执行
            onCancelled();
        } else {
            //调用模板方法4：线程执行完毕
            onPostExecute(result);
        }
    }

    /*
     *预执行，线程启动之前调用
     * */
    public void onPreExecute(){}
    /*
     *执行后台任务，在新开辟的线程中调用
     * 泛型参数由子类传递，写成抽象方法
     * */
    public abstract Result doInBackground(Params params);
    /*
     *执行进度反馈，应该在Handler中调用
     * */
    public void onProgressUpdate(Progress progress) {}
    /*
     *执行完毕，应该在Handler中调用
     */
    public void onPostExecute(Result result) {
    }
    /*
     * 终止线程执行，应该在Handler中调用
     */
    public void onCancelled() {
    }

    public final boolean isCancelled() {
        return mCancelled.get();
    }

    private static abstract class WorkerRunnable<Params, Result> implements Callable<Result> {
        Params mParams;
    }

    private static class InternalHandler extends Handler {
        /*
        * 获得在创建hander时就获取mainLooper
        * */
        public InternalHandler() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            AsyncTaskResult<?> result = (AsyncTaskResult<?>) msg.obj;
            switch (msg.what) {
                case MESSAGE_POST_RESULT:
                    //调用模板方法4和5，取消或者完成
                    result.mTask.finish(result.mData);
                    break;
                case MESSAGE_POST_PROGRESS:
                    //调用模板方法3-执行进度反馈
                    result.mTask.onProgressUpdate(result.mData);
                    break;
            }
        }
    }

    /**
     * 由于InternalHandler是静态内部类，无法引用外部类SimpleAsyncTask的实例对象，
     * 因此需要将外部类对象作为属性传递进来，所以封装此类
     */
    private static class AsyncTaskResult<Data> {
        final MyAsyncTask mTask;
        final Data mData;

        AsyncTaskResult(MyAsyncTask task, Data data) {
            mTask = task;
            mData = data;
        }
    }
}