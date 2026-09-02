# 1단계: 빌드 — node로 React를 정적 파일로 만든다
FROM node:20-alpine AS builder
WORKDIR /app

# package.json을 먼저 복사해야 소스만 바뀌었을 때 npm ci 레이어가 캐시된다
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# 2단계: 실행 — 빌드 산출물(dist)만 nginx에 얹는다. node는 이미지에 남지 않는다
FROM nginx:alpine AS runtime

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
