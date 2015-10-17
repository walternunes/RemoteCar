package jnuneslab.com.remotecar.enums;

// Command chart:
// [0] = 0 => stop                 // [2] = 0 => light off
// [0] = 1 => acc speed 1          // [2] = 1 => light on
// [0] = 2 => acc speed 2          // [2] = 2 => lantern
// [0] = 3 => acc speed 3          // [3] = 0 => no sign
// [0] = 4 => rev speed 1          // [3] = 1 => right sign
// [0] = 5 => rev speed 2          // [3] = 2 => left sign
// [0] = 6 => rev speed 3
// [1] = 0 => straight
// [1] = 1 => left
// [1] = 2 => right

public enum ControlEnum {


    ACCELARETION_SPEED_LEVEL_COMMAND{
        @Override
        public Integer getId() {

            return 0;
        }
    },

    DIRECTION_COMMAND{
        @Override
        public Integer getId() {

            return 1;
        }
    },

    HIGHLIGHTS_COMMAND{
        @Override
        public Integer getId() {

            return 2;
        }
    },

    SIGNALIZATION_COMMAND{
        @Override
        public Integer getId() {

            return 3;
        }
    };

    /* Value that represents the command to make car stop */
    public final byte STOP = '0';

    /* Value that represents the command to make the car go forward in speed level 1 */
    public final byte ACCELERATION_SPEED_LEVEL1 = '1';

    /* Value that represents the command to make the car go forward in speed level 2 */
    public final byte ACCELERATION_SPEED_LEVEL2 = '2';

    /* Value that represents the command to make the car go forward in speed level 3 */
    public final byte ACCELERATION_SPEED_LEVEL3 = '3';

    /* Value that represents the command to make the car go backward in speed level 1 */
    public final byte REVERT_SPEED_LEVEL1 = '4';

    /* Value that represents the command to make the car go backward in speed level 2 */
    public final byte REVERT_SPEED_LEVEL2 = '5';

    /* Value that represents the command to make the car go backward in speed level 3 */
    public final byte REVERT_SPEED_LEVEL3 = '6';

    /* Value that represents the command to make the car go straight ahead */
    public final byte STRAIGHT_AHEAD = '0';

    /* Value that represents the command to make the car turn right */
    public final byte TURN_LEFT = '1';

    /* Value that represents the command to make the car turn left */
    public final byte TURN_RIGHT = '2';

    /* Value that represents the command to turn off the highlights */
    public final byte HIGHLIGHT_OFF = '0';

    /* Value that represents the command to turn on the highlights */
    public final byte HIGHLIGHT_ON = '1';

    /* Value that represents the command to turn off the signalization lights */
    public final byte SIGN_OFF = '0';

    /* Value that represents the command to turn on the right signalization lights */
    public final byte SIGN_TURN_RIGHT = '1';

    /* Value that represents the command to turn on the left signalization lights */
    public final byte SIGN_TURN_LEFT = '2';

    /* Value that represents the command to turn on all signalization lights */
    public final byte SIGN_ALERT = '3';


    /**
     * Method responsible to get the enum identifier used to represent the index of control array sent to the remote car
     *
     * @return Enumeration identificator
     */
    public abstract Integer getId();


}
