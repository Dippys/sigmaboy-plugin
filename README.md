## SigmaBoy Plugin

### Overview
SigmaBoy is a plugin for the Habbo Emulator that adds new commands and functionalities. This plugin includes commands like `:afk` to toggle AFK status and `:gotw` to give GOTW points to users.

### Prerequisites
- Java 9 or higher
- Maven
- IntelliJ IDEA

### Getting Started

#### Step 1: Clone and Install Habbo Dependency
1. Clone the Habbo Emulator repository:
   ```sh
   git clone https://git.krews.org/morningstar/Arcturus-Community.git
   ```
2. Open the cloned repository in IntelliJ IDEA.
3. Run `mvn install` to install the Habbo dependency into your local Maven repository.

#### Step 2: Setup SigmaBoy Plugin
1. Clone the SigmaBoy Plugin repository:
   ```sh
   git clone https://github.com/dippys/sigmaboy-plugin
   ```
2. Open the SigmaBoy Plugin project in IntelliJ IDEA.
3. Open the `pom.xml` file and click the Maven icon (M) to reload the dependencies.

### Usage
1. To compile the plugin, run the following Maven command in the maven tab on the right:

    lifecycle > package
    
   This will generate a JAR file in the `target` directory.

2. Place the generated JAR file into the `plugins` directory of your Habbo Emulator.

3. Start the Habbo Emulator. The SigmaBoy plugin will be loaded automatically.

### Configuration
The plugin uses the following configuration keys:
- `sigmaboy.afk_effect_id`: Effect ID for AFK status.
- `sigmaboy.afk_update_interval_seconds`: Interval for AFK status updates.
- `sigmaboy.afk_status_update_interval_seconds`: Interval for AFK status messages.
- `sigmaboy.gotw_min_timer`: Minimum timer for GOTW command cooldown.
- `sigmaboy.gotw_bypass_timer_rank`: Rank that bypasses the GOTW command cooldown.
- `sigmaboy.gotw_point_type`: Point type for GOTW points.
- `sigmaboy.gotw_point_amt`: Default amount of GOTW points.

### Commands
- `:afk` - Toggle AFK status.
- `:gotw [user] [amt]` - Give GOTW points to the user (admins only).

### Author
- Dippy

### License
This project is licensed under the MIT License.