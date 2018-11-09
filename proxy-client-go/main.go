package main

import (
	"crypto/tls"
	"crypto/x509"
	"encoding/binary"
	"fmt"
	"github.com/urfave/cli"
	"io/ioutil"
	"log"
	"net"
	"os"
	"strconv"
	"time"
)

const (

	/* 认证消息，检测clientKey是否正确 */
	C_TYPE_AUTH = 0x01

	/* 代理后端服务器建立连接消息 */
	TYPE_CONNECT = 0x03

	/* 代理后端服务器断开连接消息 */
	TYPE_DISCONNECT = 0x04

	/* 代理数据传输 */
	P_TYPE_TRANSFER = 0x05

	/* 用户与代理服务器以及代理客户端与真实服务器连接是否可写状态同步 */
	C_TYPE_WRITE_CONTROL = 0x06

	/* 心跳消息 */
	TYPE_HEARTBEAT = 0x07

	//协议各字段长度
	LEN_SIZE = 4

	TYPE_SIZE = 1

	SERIAL_NUMBER_SIZE = 8

	URI_LENGTH_SIZE = 1

	//心跳周期，服务器端空闲连接如果60秒没有数据上报就会关闭连接
	HEARTBEAT_INTERVAL = 30
)

type LPMessageHandler struct {
	connPool    *ConnHandlerPool
	connHandler *ConnHandler
	clientKey   string
	die         chan struct{}
}

type Message struct {
	Type         byte
	SerialNumber uint64
	Uri          string
	Data         []byte
}

type ProxyConnPooler struct {
	addr string
	conf *tls.Config
}

func main() {
	log.Println("远程维护 - 使用此软件可以做到内网穿透 实现对设备的远程访问与维护")
	app := cli.NewApp()
	app.Name = "lanproxy"
	app.Flags = []cli.Flag{
		cli.StringFlag{
			Name:  "k",
			Value: "",
			Usage: "客户端唯一秘钥",
		},
		cli.StringFlag{
			Name:  "s",
			Value: "",
			Usage: "代理服务器 IP",
		},
		cli.IntFlag{
			Name:  "p",
			Value: 4900,
			Usage: "代理服务器代理端口",
		}, cli.StringFlag{
			Name:  "ssl",
			Value: "false",
			Usage: "是否开启 SSL 默认不开启",
		}, cli.StringFlag{
			Name:  "cer",
			Value: "",
			Usage: "SSL 证书 默认跳过认证",
		}}
	app.Usage = "使用此软件可以做到内网穿透 实现对设备的远程访问与维护"
	app.Action = func(c *cli.Context) error {
		if c.String("s") == "" {
			log.Println("代理服务器IP为必填参数, 使用 -s")
			log.Println("退出")
			return nil
		}
		if c.String("k") == "" {
			log.Println("客户端唯一秘钥为必填参数, 使用 -k")
			log.Println("退出")
			return nil
		}
		log.Println("客户端唯一秘钥:", c.String("k"))
		log.Println("代理服务器 IP:", c.String("s"))
		log.Println("代理服务器端口:", c.Int("p"))
		log.Println("是否开启 SSL :", c.String("ssl"))
		cerPath := c.String("cer")
		if c.String("cer") == "" {
			cerPath = "SSL 证书路径为空 跳过认证"
		}
		log.Println("SSL 证书路径:", cerPath)
		var conf *tls.Config
		if c.String("ssl") == "true" {
			skipVerify := false
			if c.String("cer") == "" {
				skipVerify = true
			}
			conf = &tls.Config{
				InsecureSkipVerify: skipVerify,
			}

			if c.String("cer") != "" {
				cert, err := ioutil.ReadFile(c.String("cer"))
				if err != nil {
					log.Fatalf("Couldn't load file", err)
					return nil
				}
				certPool := x509.NewCertPool()
				certPool.AppendCertsFromPEM(cert)
				conf.ClientCAs = certPool
			}
		}
		start(c.String("k"), c.String("s"), c.Int("p"), conf)
		return nil
	}

	app.Run(os.Args)
}

func start(key string, ip string, port int, conf *tls.Config) {
	connPool := &ConnHandlerPool{Size: 100, Pooler: &ProxyConnPooler{addr: ip + ":" + strconv.Itoa(port), conf: conf}}
	connPool.Init()
	connHandler := &ConnHandler{}
	for {
		//cmd connection
		conn := connect(key, ip, port, conf)
		connHandler.conn = conn
		messageHandler := LPMessageHandler{connPool: connPool}
		messageHandler.connHandler = connHandler
		messageHandler.clientKey = key
		messageHandler.startHeartbeat()
		log.Println("开始监听消息 , 客户端唯一ID:", fmt.Sprintf("%#v", messageHandler.clientKey))
		connHandler.Listen(conn, &messageHandler)
	}
}

func connect(key string, ip string, port int, conf *tls.Config) net.Conn {
	for {
		var conn net.Conn
		var err error
		p := strconv.Itoa(port)
		if conf != nil {
			conn, err = tls.Dial("tcp", ip+":"+p, conf)
		} else {
			conn, err = net.Dial("tcp", ip+":"+p)
		}
		if err != nil {
			log.Println("连接发生异常：", err.Error())
			time.Sleep(time.Second * 3)
			continue
		}

		return conn
	}
}

func (messageHandler *LPMessageHandler) Encode(msg interface{}) []byte {
	if msg == nil {
		return []byte{}
	}

	message := msg.(Message)
	uriBytes := []byte(message.Uri)
	bodyLen := TYPE_SIZE + SERIAL_NUMBER_SIZE + URI_LENGTH_SIZE + len(uriBytes) + len(message.Data)
	data := make([]byte, LEN_SIZE, bodyLen+LEN_SIZE)
	binary.BigEndian.PutUint32(data, uint32(bodyLen))
	data = append(data, message.Type)
	snBytes := make([]byte, 8)
	binary.BigEndian.PutUint64(snBytes, message.SerialNumber)
	data = append(data, snBytes...)
	data = append(data, byte(len(uriBytes)))
	data = append(data, uriBytes...)
	data = append(data, message.Data...)
	return data
}

func (messageHandler *LPMessageHandler) Decode(buf []byte) (interface{}, int) {
	lenBytes := buf[0:LEN_SIZE]
	bodyLen := binary.BigEndian.Uint32(lenBytes)
	if uint32(len(buf)) < bodyLen+LEN_SIZE {
		return nil, 0
	}
	n := int(bodyLen + LEN_SIZE)
	body := buf[LEN_SIZE:n]
	msg := Message{}
	msg.Type = body[0]
	msg.SerialNumber = binary.BigEndian.Uint64(body[TYPE_SIZE : SERIAL_NUMBER_SIZE+TYPE_SIZE])
	uriLen := uint8(body[SERIAL_NUMBER_SIZE+TYPE_SIZE])
	msg.Uri = string(body[SERIAL_NUMBER_SIZE+TYPE_SIZE+URI_LENGTH_SIZE : SERIAL_NUMBER_SIZE+TYPE_SIZE+URI_LENGTH_SIZE+uriLen])
	msg.Data = body[SERIAL_NUMBER_SIZE+TYPE_SIZE+URI_LENGTH_SIZE+uriLen:]
	return msg, n
}

func (messageHandler *LPMessageHandler) MessageReceived(connHandler *ConnHandler, msg interface{}) {
	message := msg.(Message)
	switch message.Type {
	case TYPE_CONNECT:
		go func() {
			log.Println("received connect message:", message.Uri, "=>", string(message.Data))
			addr := string(message.Data)
			realServerMessageHandler := &RealServerMessageHandler{LpConnHandler: connHandler, ConnPool: messageHandler.connPool, UserId: message.Uri, ClientKey: messageHandler.clientKey}
			conn, err := net.Dial("tcp", addr)
			if err != nil {
				log.Println("connect realserver failed", err)
				realServerMessageHandler.ConnFailed()
			} else {
				connHandler := &ConnHandler{}
				connHandler.conn = conn
				connHandler.Listen(conn, realServerMessageHandler)
			}
		}()
	case P_TYPE_TRANSFER:
		if connHandler.NextConn != nil {
			connHandler.NextConn.Write(message.Data)
		}
	case TYPE_DISCONNECT:
		if connHandler.NextConn != nil {
			connHandler.NextConn.NextConn = nil
			connHandler.NextConn.conn.Close()
			connHandler.NextConn = nil
		}
		if messageHandler.clientKey == "" {
			messageHandler.connPool.Return(connHandler)
		}
	}
}

func (messageHandler *LPMessageHandler) ConnSuccess(connHandler *ConnHandler) {
	log.Println("与服务端建立连接成功 客户端唯一ID:", messageHandler.clientKey)
	if messageHandler.clientKey != "" {
		msg := Message{Type: C_TYPE_AUTH}
		msg.Uri = messageHandler.clientKey
		connHandler.Write(msg)
	}
}

func (messageHandler *LPMessageHandler) ConnError(connHandler *ConnHandler) {
	log.Println("连接失败:", connHandler)
	if messageHandler.die != nil {
		close(messageHandler.die)
	}

	if connHandler.NextConn != nil {
		connHandler.NextConn.NextConn = nil
		connHandler.NextConn.conn.Close()
		connHandler.NextConn = nil
	}

	connHandler.messageHandler = nil
	messageHandler.connHandler = nil
	time.Sleep(time.Second * 3)
}

func (messageHandler *LPMessageHandler) startHeartbeat() {
	log.Println("开启心跳:", messageHandler.connHandler)
	messageHandler.die = make(chan struct{})
	go func() {
		for {
			select {
			case <-time.After(time.Second * HEARTBEAT_INTERVAL):
				if time.Now().Unix()-messageHandler.connHandler.ReadTime >= 2*HEARTBEAT_INTERVAL {
					log.Println("连接超时:", messageHandler.connHandler, time.Now().Unix()-messageHandler.connHandler.ReadTime)
					messageHandler.connHandler.conn.Close()
					return
				}
				msg := Message{Type: TYPE_HEARTBEAT}
				messageHandler.connHandler.Write(msg)
			case <-messageHandler.die:
				return
			}
		}
	}()
}

func (pooler *ProxyConnPooler) Create(pool *ConnHandlerPool) (*ConnHandler, error) {
	var conn net.Conn
	var err error
	if pooler.conf != nil {
		conn, err = tls.Dial("tcp", pooler.addr, pooler.conf)
	} else {
		conn, err = net.Dial("tcp", pooler.addr)
	}

	if err != nil {
		log.Println("Error dialing:", err.Error())
		return nil, err
	} else {
		messageHandler := LPMessageHandler{connPool: pool}
		connHandler := &ConnHandler{}
		connHandler.Active = true
		connHandler.conn = conn
		connHandler.messageHandler = interface{}(&messageHandler).(MessageHandler)
		messageHandler.connHandler = connHandler
		messageHandler.startHeartbeat()
		go func() {
			connHandler.Listen(conn, &messageHandler)
		}()
		return connHandler, nil
	}
}

func (pooler *ProxyConnPooler) Remove(conn *ConnHandler) {
	conn.conn.Close()
}

func (pooler *ProxyConnPooler) IsActive(conn *ConnHandler) bool {
	return conn.Active
}
