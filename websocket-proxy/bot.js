require('dotenv').config();
const { Client, GatewayIntentBits, REST, Routes, SlashCommandBuilder, Events } = require('discord.js');
const axios = require('axios');
const https = require('https');

// Setup client
const client = new Client({
  intents: [GatewayIntentBits.Guilds],
});

// Slash command definition
const commands = [
  new SlashCommandBuilder()
    .setName('players')
    .setDescription('Check how many players are online in Stick Arena.')
    .setDefaultMemberPermissions(0)
    .toJSON(),
];

const rest = new REST({ version: '10' }).setToken(process.env.DISCORD_TOKEN);

client.once('ready', async () => {
  console.log(`✅ Logged in as ${client.user.tag}`);

  // Register slash commands in both guilds
  const guilds = [process.env.GUILD_ID_1, process.env.GUILD_ID_2];

  for (const guildId of guilds) {
    try {
      await rest.put(
        Routes.applicationGuildCommands(process.env.CLIENT_ID, guildId),
        { body: commands }
      );
      console.log(`✅ Slash command /players registered to guild ${guildId}`);
    } catch (err) {
      console.error(`❌ Failed to register slash command in guild ${guildId}:`, err);
    }
  }

  // Run check immediately + every 30 minutes
  checkAndSend();
  setInterval(checkAndSend, 30 * 60 * 1000);
});

client.on(Events.InteractionCreate, async (interaction) => {
  if (!interaction.isChatInputCommand()) return;

  if (interaction.commandName === 'players') {
    try {
      const res = await axios.get('https://98.84.151.65/stickarena/discord.php', {
        httpsAgent: new https.Agent({ rejectUnauthorized: false }),
      });

      const { status, count } = res.data;

      if (status === 'ok') {
        await interaction.reply(`🎮 There are currently **${count} players** online in Stick Arena.`);
      } else {
        await interaction.reply('❌ Failed to fetch player count.');
      }
    } catch (err) {
      console.error(err.message);
      await interaction.reply('❌ Error fetching player count.');
    }
  }
});

async function checkAndSend() {
  const now = new Date();
  const estHour = now.getUTCHours() - 4;

  if (estHour >= 12 && estHour < 24) {
    try {
      const res = await axios.get('https://98.84.151.65/stickarena/discord.php', {
        httpsAgent: new https.Agent({ rejectUnauthorized: false }),
      });

      const { status, count } = res.data;

      if (status === 'ok' && count >= 3) {
        const channelIds = [process.env.CHANNEL_ID_1, process.env.CHANNEL_ID_2];
        for (const id of channelIds) {
          const channel = await client.channels.fetch(id);
          await channel.send(`🔥 There are currently **${count} players** online in Stick Arena!`);
        }
      } else {
        console.log(`Only ${count} player(s) online. No message sent.`);
      }
    } catch (err) {
      console.error('Error during checkAndSend:', err.message);
    }
  }
}

client.login(process.env.DISCORD_TOKEN);
