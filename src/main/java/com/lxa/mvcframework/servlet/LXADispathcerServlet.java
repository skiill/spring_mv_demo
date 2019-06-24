package com.lxa.mvcframework.servlet;

import com.lxa.mvcframework.annotation.LXAAutoWired;
import com.lxa.mvcframework.annotation.LXAController;
import com.lxa.mvcframework.annotation.LXARequestMapping;
import com.lxa.mvcframework.annotation.LXAService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public class LXADispathcerServlet extends HttpServlet {



    private Properties contextConfig = new Properties();

    private List<String> classNames = new ArrayList<>();

    private Map<String,Object> ioc = new HashMap<>();

    private Map<String,Method> handlerMapping = new HashMap<>();

    @Override
    public void init(ServletConfig config) throws ServletException {

        //1、加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));
        //2、解析配置文件，扫描所有相关的类
        try {
            doScanner(contextConfig.getProperty("scanPackage"));
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        //3、初始化所有的相关的类，并保存到IOC容器中
        doInstance();
        //4、完成自动化的依赖注入
        doAutoWired();
        //5、创建HandlerMapping，将URL和Method建立对应关系
        initHandlerMapping();

        System.out.println(" Spring MVC is run");
    }

    private void doScanner(String scanPackage) throws MalformedURLException {

        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.", "/"));
        URL ur2 = new URL(url.toString().replaceAll("/%5c","/"));
        File clssDir = new File(ur2.getFile());

        for (File file : clssDir.listFiles()) {

            //递归判断
            if (file.isDirectory()) {
                doScanner(scanPackage + "." + file.getName());
            } else {
                if (!file.getName().contains(".class")) {
                    continue;
                }
                //将所有相关的类放入容器中
                String className = scanPackage + "." + file.getName().replace(".class", "").trim();
                classNames.add(className);
            }
        }

    }


    private void doInstance() {
        if (classNames.isEmpty()) {
            return;
        }
        try {
            for (String className : classNames){
                Class<?> clazz = Class.forName(className);

                // 不是所有的类需要初始化,过滤
                if(clazz.isAnnotationPresent(LXAController.class) ){
                    String beanName = lowFirstCast(clazz.getSimpleName());
                    Object instance = clazz.newInstance();
                    ioc.put(beanName, instance);

                }else if(clazz.isAnnotationPresent(LXAService.class)){

                    //1.类名首字母小写
                    //2.自定义的名字（优先）

                    LXAService service = clazz.getAnnotation(LXAService.class);
                    String beanName = service.value();
                    if ("".equals(beanName.trim())){
                        //说明没有自定义名字,用默认首字母小写
                        beanName = lowFirstCast(clazz.getSimpleName());
                    }
                    Object instance = clazz.newInstance();
                    ioc.put(beanName,instance);
                    //3.用接口的全称作为key，用接口的实现类做为值
                    Class<?>[] interfaces = clazz.getInterfaces();
                    for (Class<?> i : interfaces){
                        if (ioc.containsKey(i.getName())){
                            throw new Exception("the beaName has exits");
                        }
                        ioc.put(i.getName(),instance);
                    }

                }else{
                    continue;
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }


    }

    private String lowFirstCast(String simpleName) {
        char[] chars = simpleName.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }

    private void doAutoWired() {

        if (ioc.isEmpty()){return;}
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Field[] declaredFields = entry.getValue().getClass().getDeclaredFields();

            for (Field field : declaredFields) {

                //只有注解了才初始化
                if (!field.isAnnotationPresent(LXAAutoWired.class)){continue; }

                LXAAutoWired autoWired = field.getAnnotation(LXAAutoWired.class);
                String beanName = autoWired.value();

                //使用了默认的名称
                if ("".equals(beanName)){
                    beanName = field.getType().getName();
                }

                //强制将不可见的属性可见化（private-》 public）
                field.setAccessible(true);
                try {
                    field.set(entry.getValue(),ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    continue;
                }

            }
        }

    }

    private void initHandlerMapping() {

        if (ioc.isEmpty()){return;}

        for (Map.Entry<String, Object> entry : ioc.entrySet()) {

            Class<?> clazz = entry.getValue().getClass();

            if (!clazz.isAnnotationPresent(LXAController.class)){continue;}
            String beanUrl = "";
            if (clazz.isAnnotationPresent(LXARequestMapping.class)){
                LXARequestMapping requestMapping = clazz.getAnnotation(LXARequestMapping.class);
                beanUrl = requestMapping.value();

            }

            Method[] methods = clazz.getMethods();

            for (Method method : methods){
                if (!method.isAnnotationPresent(LXARequestMapping.class)){continue;}

                LXARequestMapping requestMapping = method.getAnnotation(LXARequestMapping.class);
                //用正则将多个斜杠替换为单个斜杠
                String url = ("/" + beanUrl + "/" + requestMapping.value()).replaceAll("/+","/");
                handlerMapping.put(url,method);

                System.out.println("Mapped:" + url + "," + method);
            }

        }

    }


    private void doLoadConfig(String config) {
        InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream(config);
        try {
            contextConfig.load(resourceAsStream);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (null != resourceAsStream) {
                try {
                    resourceAsStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    private void doDispather(HttpServletRequest req, HttpServletResponse resp) throws Exception {

        if (this.handlerMapping.isEmpty()){return;}
        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replace(contextPath,"").replaceAll("/+","/");

        if (!this.handlerMapping.containsKey(url)){
            resp.getWriter().write("404 Not Found");
            return;
        }

        Method method = this.handlerMapping.get(url);

        Map<String, String[]> params = req.getParameterMap();
        Class<?> declaringClass = method.getDeclaringClass();
        String simpleName = declaringClass.getSimpleName();
        String beanName = lowFirstCast(simpleName);


        Object name = method.invoke(ioc.get(beanName), new Object[]{req, resp, params.get("name")[0]});
        resp.getWriter().write(name.toString());

    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        //6.运行
        try {
            doDispather(req,resp);
        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().write("500 Exception" + Arrays.toString(e.getStackTrace()));
        }
    }


}
