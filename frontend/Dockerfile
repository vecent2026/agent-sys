# 构建阶段
FROM node:alpine as builder
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

# 运行阶段
FROM node:alpine
RUN npm install -g serve
COPY --from=builder /app/dist /app
EXPOSE 80
CMD ["serve", "-s", "/app", "-l", "80"]
