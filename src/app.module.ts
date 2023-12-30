import { Module } from '@nestjs/common';
import { WebsocketGateway } from './websocket/websocket.gateway';

@Module({
    imports: [],
    providers: [WebsocketGateway],
})
export class AppModule {}
