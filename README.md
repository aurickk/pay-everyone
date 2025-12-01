
<p align="center">


<img src="https://github.com/user-attachments/assets/14690fb8-6bfc-4e4c-8fed-d533ec0c3781" alt="fake-mister-beast" width="15%"/>
</p>
<h1 align="center">Pay Everyone</h1>

<p align="center">A client-side Fabric mod for Minecraft 1.21.x that automatically scans and pay all online players on multiplayer servers using the `/pay` command with customizable delays, player exclusions, and more player discovery features.</p>


## Features

- **Pay All Players**: Automatically pay all online players with a single command
- **Random Amounts**: Support for random payment amounts within a range (e.g., 300-3000)
- **Customizable Delays**: Set delay between each payment to prevent command spam kick
- **Player Exclusions**: Exclude specific players from receiving payments
- **Player Logging**: Logging online player in large servers where not all players are on the tab list
- **Randomized Payment Order**: Payments are sent in random order to avoid patterns
- **Auto-Confirm**: Automatically click confirmation buttons on servers with payment confirmation menus
- **Keybinds**: Quick stop keybind (default: K) to instantly stop payments

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
### Demo Video

https://github.com/user-attachments/assets/b05579a0-08e7-49ba-9e8b-71665d6cddd4 

### Basic Commands

#### Show Pay info
```
/payall info
```
Displays a lists of logged players and excluded players on the pay list.
#### Tab Scan
```
/payall tabscan                    # Reads through /pay autofills to find avaliable players to pay
/payall tabscan stop               # Stop scan in progress
```

Tab scan queries the server's `/pay` command autocomplete to discover players beyond the tab list limit. This is useful for large servers with hundreds of players that could not be include in the tab list.

**Note**: Moving while tab scan is in progress disrupts the process

**Tip**: You can also press the **K** key (default keybind) to instantly stop tab scan

#### Pay All Players
```
/payall <amount> <delay>
```
- `amount`: Payment amount 
- `delay`: Milliseconds between each payment, left on blank default is `1000` (recommended to prevent kick).

Pay all players sequentialy from the pay list in a random order.

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

**Tip**: You can also press the **K** key (default keybind) to instantly stop payments

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
### Auto-Confirm (Confirmation Menus)

Some servers show a confirmation menu before processing each payment. The auto-confirm feature automatically clicks the confirm button for you.

#### Set Confirm Slot
```
/payall confirmclickslot <slot>           # Enable auto-click on slot ID
/payall confirmclickslot <slot> <delay>   # Enable with custom delay (50-2000ms)
/payall confirmclickslot                  # Show current status
/payall confirmclickslot off              # Disable auto-confirm
```
<img width="250" height="250" alt="image" src="https://github.com/user-attachments/assets/d1d47821-481e-4a63-9856-e0f281ebb0af" />


Chest inventory with slot IDs for reference

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

 **Auto-Confirm**: 
   - When enabled, monitors for confirmation menus opening
   - Automatically clicks the specified slot after a short delay
   - Only active during payment process
   - Configurable slot ID and click delay

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
   ./gradlew clean build "-PMC_VERSION=[MINECRAFT_VERSION]"
   ```

3. Built JARs will be in `build/libs/`:
   - `pay-everyone-[MINECRAFT_VERSION]-[MOD_VERSIOn].jar`

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






