import numbers
import sys
import os

work_dir = os.getcwd()
print('Number of args: ', len(sys.argv), 'arguments')
print('args list: ', str(sys.argv))
def addition():
    print(sys.argv[1])
    num1 = sys.argv[1]
    num2 = sys.argv[2]
    sum = int(num1) + int(num2)
    print('sum of {0} and {1} is {2}'.format(num1, num2, sum))

addition()