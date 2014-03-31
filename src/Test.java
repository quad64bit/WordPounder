/**
 * Created by IntelliJ IDEA.
 * User: sgates
 * Date: 3/13/12
 * Time: 11:24 AM
 * To change this template use File | Settings | File Templates.
 */
public class Test {
    public static void main(String[] args) {
        new Test();
    }

    public Test() {
        int[] l = {1,2,3};
        System.out.println(l[0] + " " + l[1] + " " + l[2] + " ");
        mod(l);
        System.out.println(l[0] + " " + l[1] + " " + l[2] + " ");
    }

    private void mod(int[] list){
        list[0] = 9;
        list[1] = 9;
        list[2] = 9;
    }
}
