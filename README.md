
<p align="center">


<img src="https://github.com/user-attachments/assets/14690fb8-6bfc-4e4c-8fed-d533ec0c3781" alt="fake-mister-beast" width="15%"/>
</p>
<h1 align="center">Pay Everyone</h1>

<p align="center">A client-side Fabric mod for Minecraft 1.21.x that automatically scans and pay all online players on muliplayer servers using the `/pay` command with customizable delays, player exclusions, and more player discovery features.</p>


## Features

- **Pay All Players**: Automatically pay all online players with a single command
- **Random Amounts**: Support for random payment amounts within a range (e.g., 300-3000)
- **Customizable Delays**: Set delay between each payment to prevent command spam kick
- **Player Exclusions**: Exclude specific players from receiving payments
- **Advanced Player Discovery**: 
  - Logging online player in large servers where not all players are on the tab list
- **Randomized Payment Order**: Payments are sent in random order to avoid patterns

## Requirements

- **Minecraft**: 1.21.4 or 1.21.8
- **Fabric Loader**: 0.15.0 or higher
- **Fabric API**: Latest version for your Minecraft version

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for your Minecraft version
2. Download the latest [Fabric API](https://modrinth.com/mod/fabric-api) for your Minecraft version
3. Download the latest `pay-everyone-[version]-[mod_version].jar` from the [Releases](https://github.com/aurickk/pay-everyone/releases/) page
4. Place both mods in your `.minecraft/mods` folder
5. Launch Minecraft

## Usage

### Basic Commands

#### Show Pay info
```
/payall info
```
<img width="570" height="105" alt="Screenshot 2025-11-30 130108" src="https://github.com/user-attachments/assets/fcd8f183-0183-4341-a3d1-a41588f4ef62" />

Displays a lists of logged players and excluded players on the pay list.
#### Tab Scan
```
/payall tabscan                    # Reads through /pay autofills to find avaliable players to pay
/payall tabscan stop               # Stop scan in progress
```

Tab scan queries the server's `/pay` command autocomplete to discover players beyond the tab list limit. This is useful for large servers with thousands of players that could not be include in the tab list.

**Note**: Moving while tab scan is in progress disrupts the process


#### Pay All Players
```
/payall <amount> <delay>
```
- `amount`: Payment amount 
- `delay`: Milliseconds between each payment, left on blank default is `1000` (recommended to prevent kick).

**Example with random amounts:**
```
/payall 300-3000 
```
This will pay each player a random amount between 300 and 3000

#### Stop Payment Process
```
/payall stop
```
Stops the current payment process if one is running.



#### Exclude Players
```
/payall exclude <player1> <player2>
/payall exclude Player1 Player2
```
Exclude specific players from receiving payments. Autocomplete is available showing players from your pay list.

#### Add Players to Pay List
```
/payall add <player1> <player2> <player3>
/payall add Player1 Player2 Player3
```
Manually add players to the payment list. 

#### Remove Manually Added Players 
```
/payall remove exclude <player1> <player2>    # Remove from exclusion list
/payall remove add <player1> <player2>       # Remove from manual add list
```

#### Clear Lists
```
/payall clear exclude      # Clear excluded players
/payall clear add          # Clear manually added players
/payall clear tabscan      # Clear tab scan results
/payall clear all          # Clear everything
```

#### View Player Lists
```
/payall info                       # Show payment information
/payall list                       # Show debug info
/payall list tabscan               # List tab scan players
/payall list add                   # List manually added players
/payall list exclude                # List excluded players
/payall list tablist                # List default tab menu players
```
## How It Works

 **Player Discovery**: The mod collects players from multiple sources:
   - Default tab menu (limited to ~150 players)
   - Tab scan (queries server autocomplete for `/pay` command)
   - Manual player list (you add players)

**Payment Process**: 
   - Player order are randomized before payment
   - Wait for a set interval between each command excution
   - Excluded players are skipped
   - Process can be stopped at any time

**Tab Scan**: 
   - Sends autocomplete requests for `/pay` command with different prefixes
   - Scans through: empty, a-z, 0-9, and underscore
   - Collects all player names from server responses
   - Takes time but discovers many more players

## Building from Source

### Prerequisites

- Java 21 or higher
- Gradle (included via wrapper)

### Build Steps

1. Clone the repository:
   ```bash
   git clone https://github.com/aurickk/pay-everyone/
   cd pay-everyone
   ```

2. **Windows**: Run `build.bat` and select the version to build
   
   **Linux/Mac**: Build for a specific version:
   ```bash
   ./gradlew clean build "-PMC_VERSION=1.21.4"
   ```

3. Built JARs will be in `build/libs/`:
   - `pay-everyone-1.21.4-1.0.0.jar`

### Supported Versions

The mod supports building for Minecraft versions:
- 1.21
- 1.21.1
- 1.21.2
- 1.21.3
- 1.21.4 (Tested)
- 1.21.5
- 1.21.6
- 1.21.7
- 1.21.8 (Tested)
- 1.21.9


## Troubleshooting

### Tab Scan Not Finding Players

- Ensure the server has a `/pay [player] [amount]` command
- Try increasing the scan interval: `/payall tabscan 500`
- Enable debug mode to see what's happening: `/payall tabscan debug true`
- Some servers may not support autocomplete for `/pay` - use `/payall add` instead

### Payment Commands Not Working

- Verify you have permission to use `/pay` on the server
- Ensure you're not already in a payment process (use `/payall stop`)






