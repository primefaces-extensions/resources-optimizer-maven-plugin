function add_es5(a, b) {
  return a + b;
}

function subtract_es5(a, b) {
  return a - b;
}

function multiply_es5(a, b) {
  return a * b;
}

function divide_es5(a, b) {
  if (b === 0) {
    throw new Error("Cannot divide by zero");
  }
  return a / b;
}

function average_es5(numbers) {
  if (!numbers || numbers.length === 0) {
    return 0;
  }

  var sum = 0;
  for (var i = 0; i < numbers.length; i++) {
    sum += numbers[i];
  }

  return sum / numbers.length;
}

function max_es5(numbers) {
  if (!numbers || numbers.length === 0) {
    return null;
  }

  var currentMax = numbers[0];
  for (var i = 1; i < numbers.length; i++) {
    if (numbers[i] > currentMax) {
      currentMax = numbers[i];
    }
  }

  return currentMax;
}

function factorial_es5(n) {
  if (n < 0) {
    throw new Error("Factorial is not defined for negative numbers");
  }

  var result = 1;
  for (var i = 2; i <= n; i++) {
    result *= i;
  }

  return result;
}

function calculateStats_es5(numbers) {
  var sum = 0;

  for (var i = 0; i < numbers.length; i++) {
    sum += numbers[i];
  }

  return {
    sum: sum,
    average: average_es5(numbers),
    max: max_es5(numbers)
  };
}

const add_es6 = (a, b) => a + b;

const subtract_es6 = (a, b) => a - b;

const multiply_es6 = (a, b) => a * b;

const divide_es6 = (a, b) => {
  if (b === 0) {
    throw new Error("Cannot divide by zero");
  }
  return a / b;
};

const average_es6 = (numbers = []) => {
  if (numbers.length === 0) {
    return 0;
  }

  const sum = numbers.reduce((acc, num) => acc + num, 0);
  return sum / numbers.length;
};

const max_es6 = (numbers = []) => {
  if (numbers.length === 0) {
    return null;
  }

  return Math.max(...numbers);
};

const factorial_es6 = (n) => {
  if (n < 0) {
    throw new Error("Factorial is not defined for negative numbers");
  }

  let result = 1;
  for (let i = 2; i <= n; i++) {
    result *= i;
  }

  return result;
};

const calculateStats_es6 = (numbers = []) => {
  const sum = numbers.reduce((acc, num) => acc + num, 0);

  return {
    sum,
    average: average_es6(numbers),
    max: max_es6(numbers)
  };
};
