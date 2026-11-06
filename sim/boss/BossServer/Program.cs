using System.Diagnostics;
using System.Net;
using System.Net.Sockets;

// 接続ごとにボスを1プロセス起動し、TCPとボスの標準入出力をつなぐ。
var listener = new TcpListener(IPAddress.Any, 9000);
listener.Start();
Console.WriteLine("boss server ready");
while (true)
{
    TcpClient client = await listener.AcceptTcpClientAsync();
    _ = Task.Run(() => Serve(client));
}

static async Task Serve(TcpClient client)
{
    client.NoDelay = true;
    var info = new ProcessStartInfo("dotnet", "/tmp/boss-out/Boss/Boss.dll")
    {
        RedirectStandardInput = true,
        RedirectStandardOutput = true,
        RedirectStandardError = true,
    };
    using Process boss = Process.Start(info)!;
    boss.ErrorDataReceived += (_, _) => { };
    boss.BeginErrorReadLine();
    NetworkStream stream = client.GetStream();
    Task toBoss = Copy(stream, boss.StandardInput.BaseStream);
    Task fromBoss = Copy(boss.StandardOutput.BaseStream, stream);
    await Task.WhenAny(toBoss, fromBoss);
    try { boss.Kill(); } catch (InvalidOperationException) { }
    client.Close();
}

static async Task Copy(Stream from, Stream to)
{
    byte[] buffer = new byte[65536];
    try
    {
        int read;
        while ((read = await from.ReadAsync(buffer)) > 0)
        {
            await to.WriteAsync(buffer.AsMemory(0, read));
            await to.FlushAsync();
        }
    }
    catch (IOException) { }
}
