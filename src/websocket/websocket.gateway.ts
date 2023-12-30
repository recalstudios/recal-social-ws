import {OnGatewayDisconnect, OnGatewayInit, WebSocketGateway} from "@nestjs/websockets";
import * as crypto from "crypto";
import {Connection} from "../types/connection";
import {GeneralPayload} from "../types/payloads/general-payload";
import {AuthPayload} from "../types/payloads/auth-payload";
import {AuthorizedConnection} from "../types/authorized-connection";
import {MessagePayload} from "../types/payloads/message-payload";
import axios from "axios";
import {API} from "../config";
import {Logger} from "@nestjs/common";
import {DeletePayload} from "../types/payloads/delete-payload";
import * as http from "http";

@WebSocketGateway()
export class WebsocketGateway implements OnGatewayInit, OnGatewayDisconnect
{
    private readonly logger: Logger = new Logger(WebSocketGateway.name);
    private connections: (Connection | AuthorizedConnection)[] = [];

    private createConnectionId(): string
    {
        // TODO: Make sure this connection ID has not already been used
        return crypto.randomBytes(8).toString('hex').toUpperCase();
    }

    afterInit(server: any): void
    {
        server.on('connection', (ws: WebSocket, request: http.IncomingMessage): void =>
        {
            // Declare the connection and add it to the connection list
            let thisConnection: Connection | AuthorizedConnection = new Connection(this.createConnectionId(), ws);
            this.connections.push(thisConnection);
            this.logger.log(`New connection from ${request.headers['x-forwarded-for'] || request.socket.remoteAddress}: ${thisConnection.id}, ${this.connections.length} total (${this.connections.filter(c => c instanceof AuthorizedConnection).length} authorized)`);

            // Ask the client for credentials
            ws.send(new GeneralPayload('status', 'auth').toString());

            // Register a callback for getting a message
            ws.onmessage = async (event: MessageEvent): Promise<void> =>
            {
                // Store the received data
                let payload: GeneralPayload = JSON.parse(event.data);

                // Let the client know if it sent invalid data
                if (!payload.type) return ws.send(new GeneralPayload('invalid', payload).toString());

                // Process data based on its type
                switch (payload.type)
                {
                    case 'auth':
                        // Check if the client is already authorized
                        if (thisConnection instanceof AuthorizedConnection)
                        {
                            this.logger.warn(`Connection ${thisConnection.id} tried authorizing, but it already seems authorized? Ignoring.`);
                            return ws.send(new GeneralPayload('invalid', payload).toString());
                        }

                        // Store the payload as an AuthPayload to get access to the token
                        const authPayload: AuthPayload = payload as AuthPayload;

                        // Store the index of this connection in the connection list. This will be used later if the upgrade is successful.
                        const connectionIndex = this.connections.indexOf(thisConnection);

                        // Try to upgrade the connection to an AuthorizedConnection
                        thisConnection = await thisConnection.authorize(authPayload.token);

                        // Check if the authorization was successful
                        if (thisConnection instanceof AuthorizedConnection)
                        {
                            // Update this Connection in the connection list to an AuthorizedConnection
                            this.connections[connectionIndex] = thisConnection;

                            // Return OK to the client
                            this.logger.log(`Connection ${thisConnection.id} successfully authorized!`);
                            return ws.send(new GeneralPayload('status', 'ok').toString());
                        }
                        else
                        {
                            this.logger.warn(`Incorrect authorization attempt from connection ${thisConnection.id}`);
                            return ws.send(new GeneralPayload('invalid', payload).toString());
                        }
                    case 'message':
                        // Make sure that the client is authorized
                        if (!(thisConnection instanceof AuthorizedConnection))
                        {
                            this.logger.warn(`Connection ${thisConnection.id} tried sending message without authorization`);
                            return ws.send(new GeneralPayload('invalid', payload).toString());
                        }

                        // Store the payload as a MessagePayload
                        const messagePayload: MessagePayload = payload as MessagePayload;

                        // Sanitize message
                        messagePayload.content.text = messagePayload.content.text.replaceAll('<', '&lt;');

                        // Send the message to the API
                        const messageResponse = (await axios.post(`${API}/chat/room/message/save`, messagePayload, {
                            headers: {
                                'Authorization': thisConnection.token,
                                'Content-Type': 'application/json'
                            }
                        })).data;

                        // Relay the message to connected clients in the relevant room
                        const connectionsInRoom = this.connections.filter(c => c instanceof AuthorizedConnection && c.rooms.includes(messagePayload.room));
                        this.logger.log(`Connection ${thisConnection.id} successfully relayed message to ${connectionsInRoom.length} client(s)!`);
                        return connectionsInRoom.forEach(c => c.ws.send(JSON.stringify(messageResponse)));
                    case 'delete':
                        // Make sure that the client is authorized
                        if (!(thisConnection instanceof AuthorizedConnection))
                        {
                            this.logger.warn(`Connection ${thisConnection.id} tried deleting a message without authorization`);
                            return ws.send(new GeneralPayload('invalid', payload).toString());
                        }

                        // Store the payload as a DeletePayload
                        const deletePayload: DeletePayload = payload as DeletePayload;

                        // Query the API for message deletion
                        const deleteResponse = (await axios.post(`${API}/chat/room/message/delete`, { messageId: deletePayload.id }, {
                            headers: {
                                'Authorization': thisConnection.token,
                                'Content-Type': 'application/json'
                            }
                        })).data;

                        // Check if the deletion was successful
                        if (deleteResponse)
                        {
                            this.logger.log(`Connection ${thisConnection.id} deleted message ${deletePayload.id}`);
                            return this.connections.filter(c => c instanceof AuthorizedConnection && c.rooms.includes(deletePayload.room)).forEach(c => c.ws.send(JSON.stringify(deletePayload)));
                        }
                        else
                        {
                            this.logger.warn(`Connection ${thisConnection.id} unsuccessfully tried to delete message ${deletePayload.id}`);
                            return ws.send(new GeneralPayload('invalid', payload).toString());
                        }
                    case 'system': case 'typing': default:
                        return ws.send(new GeneralPayload('invalid', payload).toString());
                }
            }
        });

        this.logger.log('Registered connection callback');
    }

    handleDisconnect(client: WebSocket): void
    {
        // Get the connection from the list and its index
        const connection: Connection | AuthorizedConnection = this.connections.find(c => c.ws === client);
        const connectionIndex: number = this.connections.indexOf(connection);

        // Remove the connection from the list
        this.connections.splice(connectionIndex, 1);

        this.logger.log(`Client ${connection.id} disconnected, ${this.connections.length} remaining`);
    }
}
