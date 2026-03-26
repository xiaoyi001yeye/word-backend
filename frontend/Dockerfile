FROM nginx:alpine

# 删除默认nginx页面
RUN rm -rf /usr/share/nginx/html/*

# 复制预构建结果到nginx目录
COPY dist /usr/share/nginx/html

# 复制自定义nginx配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 暴露端口
EXPOSE 80

# 启动nginx
CMD ["nginx", "-g", "daemon off;"]
