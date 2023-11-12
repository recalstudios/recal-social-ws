FROM gradle
EXPOSE 80
WORKDIR /data
COPY . .
CMD ["gradle", "run"]
