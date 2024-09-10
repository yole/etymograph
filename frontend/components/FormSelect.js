import {useFormContext} from "react-hook-form";

export default function FormSelect(props) {
    const {register} = useFormContext()

    return <tr>
        <td><label htmlFor={props.id}>{props.label}:</label></td>
        <td>
            <select {...register(props.id)}>
                <option value="-1">-</option>
                {props.options.map(mc => <option value={mc.id} selected={mc.id === props.selection}>{mc.text}</option>)}
            </select>
        </td>
    </tr>
}